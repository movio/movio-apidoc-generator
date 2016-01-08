package scala.models

import com.bryzek.apidoc.generator.v0.models.{InvocationForm}
import org.scalatest.{ ShouldMatchers, FunSpec }

class AdvancedCaseClassesSpec extends FunSpec with ShouldMatchers {

  val service = models.TestHelper.service(models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "models": [],
      "enums": [],
      "unions": [],
      "resources": [],
      "models": [
        {
          "name": "user",
          "plural": "users",
          "fields": [
            { "name": "age", "type": "string", "required": true, "maximum": 10, "attributes": [] },
            { "name": "date_time", "type": "string", "required": true, "attributes": [
              { "name": "scala_props", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]},
            { "name": "optional_date_time", "type": "string", "required": false, "attributes": [
              { "name": "scala_props", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]}
          ],
          "attributes": [
            { "name": "scala_props", "value": { "extends": ["com.github.BaseClass"] } }
          ]
        }
      ]
    """))

  it("generates validation") {
    val form = InvocationForm(service)
    val contents = AdvancedCaseClasses.generateCode(form, addHeader = false).map(_.contents).mkString("\n\n")
    models.TestHelper.assertEqualsFile("/extended-case-example.txt", contents)
  }
}




