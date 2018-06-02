import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.github.tototoshi.csv._

import scala.collection.mutable

object Main {

  def main(args: Array[String]): Unit = {
    println(s"file: ${args(0)}")

    val months = parse(args(0))

    val what = 6
  }

  def parse(file: String): List[(YearMonth, Balance)] = {
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
}