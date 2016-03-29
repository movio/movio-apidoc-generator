package scala.models

import scala.generator.{ScalaCaseClasses, ScalaClientMethodConfigs, ScalaService}
import com.bryzek.apidoc.generator.v0.models.InvocationForm
import org.scalatest.{ShouldMatchers, FunSpec}

class ScalaNestedUnionSpec extends FunSpec with ShouldMatchers {

  val clientMethodConfig = ScalaClientMethodConfigs.Play24("test.apidoc")

  val json = models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "enums": [],
      "resources": [],
      "attributes": [],
      "unions": [
        {
          "name": "inner_type",
          "attributes": [],
          "plural": "inner_types",
          "types": [
            {
              "type": "string_model",
              "attributes": []
            }
          ]
        },
        {
          "name": "outer_type",
          "attributes": [],
          "plural": "outer_types",
          "types": [
            {
              "type": "inner_type",
              "attributes": []
            }
          ]
        }
      ],
      "models": [
        {
          "name": "string_model",
          "attributes": [],
          "plural": "string_models",
          "fields": [
            {
              "name": "name",
              "type": "string",
              "attributes": [],
              "required": true
            }
          ]
        }
      ]
  """)

  lazy val service = models.TestHelper.service(json)
  lazy val ssd = ScalaService(service)

  it("generates valid inner type readers") {
    val innerType = ssd.unions.find(_.name == "InnerType").get
    val code = Play2JsonExtended(ssd).readers(innerType)
    models.TestHelper.assertEqualsFile("/scala-nested-union-models-json-union-type-readers-inner-type.txt", code)
  }

  it("generates valid outer type readers") {
    val outerType = ssd.unions.find(_.name == "OuterType").get
    val code = Play2JsonExtended(ssd).readers(outerType)
    models.TestHelper.assertEqualsFile("/scala-nested-union-models-json-union-type-readers-outer-type.txt", code)
  }
}
