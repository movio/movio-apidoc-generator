package scala.generator

import com.bryzek.apidoc.generator.v0.models.{InvocationForm}
import org.scalatest.{ ShouldMatchers, FunSpec }

class ExtendedCaseClassesSpec extends FunSpec with ShouldMatchers {

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
              { "name": "scala_type", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]},
            { "name": "optional_date_time", "type": "string", "required": false, "attributes": [
              { "name": "scala_type", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]}
          ],
          "attributes": [
            { "name": "scala_extends", "value": { "classes": ["co.movio.BaseClass"] } }
          ]
        }
      ]
    """))

  it("generates validation") {
    val form = InvocationForm(service)
    val contents = ExtendedCaseClasses.generateCode(form, addHeader = false).map(_.contents).mkString("\n\n")
    models.TestHelper.assertEqualsFile("/extended-case-example.txt", contents)
  }
}




