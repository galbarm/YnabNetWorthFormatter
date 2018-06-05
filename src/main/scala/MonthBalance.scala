import java.time.YearMonth

case class MonthBalance(month: YearMonth, assets: Double, debts: Double) {
  val netWorth: Double = assets - debts
}