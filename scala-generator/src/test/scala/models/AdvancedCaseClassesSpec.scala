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
      "attributes": [],
      "models": [
        {
          "name": "user",
          "plural": "users",
          "fields": [
            { "name": "string_max", "type": "string", "required": true, "maximum": 10, "attributes": [] },
            { "name": "string_min", "type": "string", "required": true, "minimum": 11, "attributes": [] },
            { "name": "string_max_list", "type": "[string]", "required": true, "maximum": 12, "attributes": [
              { "name": "field_validation", "value": { "maximum": 13 } }
            ]},
            { "name": "string_min_list", "type": "[string]", "required": true, "minimum": 14, "attributes": [
              { "name": "field_validation", "value": { "minimum": 15 } }
            ]},
            { "name": "int_max", "type": "integer", "required": true, "maximum": 16, "attributes": [] },
            { "name": "long_min", "type": "long", "required": true, "minimum": 17, "attributes": [] },
            { "name": "date_time", "type": "string", "required": true, "attributes": [
              { "name": "scala_field_props", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]},
            { "name": "optional_date_time", "type": "string", "required": false, "attributes": [
              { "name": "scala_field_props", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]},
            { "name": "string_option_max", "type": "string", "required": false, "maximum": 18, "attributes": [] }
          ],
          "attributes": [
            { "name": "scala_model_props", "value": { "extends": ["com.github.BaseClass"] } }
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
                { "name": "scala_field_props", "value":
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

  it("should test utf8 properly to support 4 bytes per code point strings. This example has special parens") {
    // We use this code to validate what we should put in the generated validation
    // http://stackoverflow.com/questions/6828076/how-to-correctly-compute-the-length-of-a-string-in-java
    val text = "\uDBFF\uDFFCsurpi\u0301se!\uDBFF\uDFFD"
    text.codePointCount(0, text.length) shouldBe 11
  }
}




