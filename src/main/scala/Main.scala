import java.io._
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util
import java.util.{Collections, Locale}
import com.github.tototoshi.csv._
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.{Sheets, SheetsScopes}
import com.google.api.services.sheets.v4.model.ValueRange
import scala.collection.mutable
import scala.io.Source

object Main {

  private val JSON_FACTORY = JacksonFactory.getDefaultInstance

    def main(args: Array[String]): Unit = {
      println(s"file: ${args(0)}")
      val items = parse(args(0))
      val months = createMonths(items)
      updateNetWorth(months)
      updatePensionPercentage(items)
      updateAssets(items)
    }

  private def parse(file: String): Seq[Item] = {
    val currency = Source.fromResource("currency.txt").mkString
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM ''uu", Locale.ENGLISH)
    val months = mutable.HashMap[YearMonth, MonthBalance]()
    val reader = CSVReader.open(new File(file))
    val accounts = reader.allWithHeaders()
    reader.close()

    accounts
      .filterNot(account => account("Account") == "Net Worth")
      .flatMap(account => {
        account.collect {
          case monthBalance if monthBalance._1 != "Account" =>
            val month = YearMonth.parse(monthBalance._1, formatter)
            val balance = monthBalance._2.replaceAll(currency, "").toDouble
            Item(month, account("Account"), balance)
        }
      })
  }

  def createMonths(items: Seq[Item]): Iterable[MonthBalance] = {
    items.groupBy(_.month).map(month => {
      val partition = month._2.partition(_.balance >= 0)
      MonthBalance(month._1, partition._1.map(_.balance).sum, partition._2.map(-_.balance).sum)
    }).toList.sortBy(_.month)
  }

  private def updateNetWorth(months: Iterable[MonthBalance]): Unit = {
    val values: util.List[util.List[AnyRef]] = new util.ArrayList()

    values.add(util.Arrays.asList("Date", "Assets", "Debts", "Net Worth"))
    months.foreach(month =>
      values.add(util.Arrays.asList(month.month.toString, month.assets.toString, month.debts.toString, month.netWorth.toString)))

    updateSpreadsheets(values, Source.fromResource("net_worth_sheet_name.txt").mkString)
  }

  private def updatePensionPercentage(items: Seq[Item]): Unit = {
    val pensionAccounts = Source.fromResource("pension_accounts.txt").getLines().toList

    val partitioned = items.filter(_.month.isAfter(YearMonth.of(2010,12)))partition(item => pensionAccounts.contains(item.account))
    val pensioned = createMonths(partitioned._1)
    val nonPensioned = createMonths(partitioned._2)

    val values: util.List[util.List[AnyRef]] = new util.ArrayList()

    values.add(util.Arrays.asList("Date", "NonPensionNetWorth", "PensionNetWorth", "Percentage"))
    pensioned.foreach(month => {
      val nonPensionedNetWorth = nonPensioned.find(_.month == month.month).get.netWorth
      values.add(util.Arrays.asList(month.month.toString, nonPensionedNetWorth.toString, month.netWorth.toString, (month.netWorth / (nonPensionedNetWorth + month.netWorth)).toString))
    })

    updateSpreadsheets(values, Source.fromResource("pension_percentage_sheet_name.txt").mkString)
  }

  private def updateAssets(items: Seq[Item]): Unit = {
    val assets = items.groupBy(_.month).maxBy(_._1)._2.filter(_.balance > 0).sortBy(_.balance).reverse

    val values: util.List[util.List[AnyRef]] = new util.ArrayList()
    values.add(util.Arrays.asList("חשבון", "מאזן"))
    assets.foreach(item => values.add(util.Arrays.asList(item.account, item.balance.toString)))

    updateSpreadsheets(values, Source.fromResource("assets_sheet_name.txt").mkString)
  }

  private def updateSpreadsheets(values: util.List[util.List[AnyRef]], sheetName: String): Unit = {
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val spreadsheetId = Source.fromResource("spreadsheet_id.txt").mkString
    val service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
      .setApplicationName("YnabNetWorthFormatter")
      .build()

    service.spreadsheets().values().update(spreadsheetId, sheetName, new ValueRange().setValues(values)).setValueInputOption("USER_ENTERED").execute()
  }

  private def getCredentials(HTTP_TRANSPORT: NetHttpTransport) = {
    val clientSecrets = GoogleClientSecrets
      .load(JSON_FACTORY, Source.fromResource("client_id.json").reader())

    val flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, Collections.singletonList(SheetsScopes.SPREADSHEETS))
      .setDataStoreFactory(new FileDataStoreFactory(new File("credentials")))
      .setAccessType("offline")
      .build

    new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver)
      .authorize("user")
  }
}