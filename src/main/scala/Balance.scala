case class Balance(assets: Double, debts: Double){
  val netWorth: Double = assets - debts
}