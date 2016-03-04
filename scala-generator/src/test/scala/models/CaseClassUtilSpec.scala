package scala.models

import scala.generator._
import org.scalatest.{ ShouldMatchers, FunSpec }

class CaseClassUtilSpec extends FunSpec with ShouldMatchers {
  import CaseClassUtil._

  val json = models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "attributes": [],
      "enums": [{
        "name": "status",
        "plural": "statuses",
        "values": [
          {
            "name": "open",
            "attributes": []
          },{
            "name": "closed",
            "attributes": []
          }
        ],
        "attributes": []
      }],
      "unions": [],
      "models": [
        {
          "name": "member",
          "attributes": [],
          "plural": "members",
          "fields": [
            { "name": "id", "type": "long", "required": true, "attributes": [] },
            { "name": "email", "type": "string", "required": true, "attributes": [] },
            { "name": "name", "type": "string", "required": false, "attributes": [] },
            { "name": "foo", "type": "string", "required": true, "attributes": [] },
            { "name": "enum", "type": "status", "required": true, "attributes": [] }
          ]
        }
      ],
      "resources": []
  """)

  lazy val service = models.TestHelper.service(json)
  lazy val ssd = ScalaService(service)

  describe("generateInstance") {
    it("is a kafka class") {
      val res = generateInstance(getModelByName("member", ssd).get, 2, ssd)
      res shouldBe """
Member (
  id = 4,
  email = "email2",
  name = None,
  foo = "foo2",
  enum = Status.Open
)
"""
    }
  }

}
