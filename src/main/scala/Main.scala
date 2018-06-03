import java.io._
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.github.tototoshi.csv._
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange

import scala.collection.mutable
import java.util
import java.util.Collections

import com.google.api.services.sheets.v4.SheetsScopes

import scala.io.Source

object Main {

  private val JSON_FACTORY = JacksonFactory.getDefaultInstance

  def main(args: Array[String]): Unit = {
    println(s"file: ${args(0)}")
    val months = parse(args(0))
    updateSheets(months)
  }

  private def parse(file: String): List[(YearMonth, Balance)] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM ''uu", Locale.ENGLISH)
    val months = mutable.HashMap[YearMonth, Balance]()
    val reader = CSVReader.open(new File(file))
    val accounts = reader.allWithHeaders()
    reader.close()

    accounts.foreach(account => {
      if (account("Account") != "Net Worth") {
        account.foreach(monthBalance => {
          if (monthBalance._1 != "Account") {
            val month = YearMonth.parse(monthBalance._1, formatter)
            val balance = months.getOrElse(month, Balance(0, 0))
            val newBalance = monthBalance._2.replaceAll("₪", "").toDouble match {
              case amount if amount >= 0 => Balance(balance.assets + amount, balance.debts)
              case amount => Balance(balance.assets, balance.debts - amount)
            }
            months.put(month, newBalance)
          }
        })
      }
    })

    months.toList.sortBy(_._1)
  }


  private def updateSheets(months: List[(YearMonth, Balance)]): Unit = {
    val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
    val spreadsheetId = Source.fromResource("spreadsheet_id.txt").mkString
    val service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
      .setApplicationName("YnabNetWorthFormatter")
      .build()


    val values: util.List[util.List[AnyRef]] = new util.ArrayList()

    values.add(util.Arrays.asList("Date", "Assets", "Debts", "Net Worth"))
    months.foreach(month =>
      values.add(util.Arrays.asList(month._1.toString, month._2.assets.toString, month._2.debts.toString, month._2.netWorth.toString)))

    service.spreadsheets().values().update(spreadsheetId, Source.fromResource("sheet_name.txt").mkString, new ValueRange().setValues(values)).setValueInputOption("USER_ENTERED").execute()
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