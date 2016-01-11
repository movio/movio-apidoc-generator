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
        },
        {
          "name": "kafka_user",
          "plural": "kafka_users",
          "fields": [
            { "name": "v0", "type": "user", "required": true, "attributes": [] },
            { "name": "utc_generated_time",
              "type": "date-iso8601",
              "required": true,
              "attributes": [
                { "name": "scala_props", "value":
                  {
                    "class": "org.joda.time.LocalDateTime",
                    "default": "org.joda.time.LocalDateTime.now(org.joda.time.DateTimeZone.UTC)"
                  }
                }
              ]
            }

          ],
          "attributes": [
            { "name": "kafka_props",
              "value": {
                "data_type": "user",
                "topic": "s\"mc-person-master-${tenant}\"",
                "message_generate_key": "java.util.UUID.randomUUID().toString"
              }
            }
          ]
        }
      ]
    """))

  it("generates validation") {
    val form = InvocationForm(service)
    val contents = AdvancedCaseClasses.generateCode(form, addHeader = false).map(_.contents).mkString("\n\n")
    models.TestHelper.assertEqualsFile("/advanced-case-example.txt", contents)
  }
}




