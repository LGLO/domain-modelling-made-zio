package ordertaking

import Types._

object TestValues {
  val abEmail = EmailAddress("a@b.com")
  val abPersonalInfo = PersonalName(String50("a"), String50("b"))
  val abCustomerInfo = CustomerInfo(abPersonalInfo, abEmail)

  val magnoliaStreet42 = Address(String50("Magnolia St. 42"), None, None, None, String50("Watopia"), ZipCode("40587"))
  val devNull0 = Address(String50("Dev Null 0"), None, None, None, String50("Segfault"), ZipCode("00000"))

  val billingAmount1 = BillingAmount.create(BigDecimal(1)).getOrElse(throw new Exception("panic!"))
}
