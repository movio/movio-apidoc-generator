package scala.models

import scala.generator.{ScalaCaseClasses, ScalaClientMethodConfigs, ScalaService}
import com.bryzek.apidoc.generator.v0.models.InvocationForm
import org.scalatest.{ShouldMatchers, FunSpec}

class ScalaUnionSpec extends FunSpec with ShouldMatchers {

  val clientMethodConfig = ScalaClientMethodConfigs.Play23("test.apidoc")

  describe("models") {

    val json = models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "enums": [],
      "resources": [],

      "unions": [
        {
          "name": "user",
          "attributes": [],
          "plural": "users",
          "types": [
            { "type": "registered_user", "attributes": [] },
            { "type": "guest_user", "attributes": [] }
          ]
        }
      ],

      "models": [
        {
          "name": "registered_user",
          "attributes": [],
          "plural": "registered_users",
          "fields": [
            { "name": "id", "type": "long", "required": true, "attributes": [] },
            { "name": "email", "type": "string", "required": true, "attributes": [] },
            { "name": "name", "type": "string", "required": false, "attributes": [] },
            { "name": "foo", "type": "string", "required": true, "attributes": [] }
          ]
        },
        {
          "name": "guest_user",
          "attributes": [],
          "plural": "guest_users",
          "fields": [
            { "name": "id", "type": "long", "required": true, "attributes": [] },
            { "name": "email", "type": "string", "required": true, "attributes": [] },
            { "name": "name", "type": "string", "required": false, "attributes": [] },
            { "name": "bar", "type": "string", "required": true, "attributes": [] }
          ]
        }
      ]
  """)

    lazy val service = models.TestHelper.service(json)
    lazy val ssd = ScalaService(service)

    it("generates valid models") {
      ScalaCaseClasses.invoke(InvocationForm(service), addHeader = false) match {
        case Left(errors) => fail(errors.mkString(", "))
        case Right(sourceFiles) => {
          sourceFiles.size shouldBe 1
          models.TestHelper.assertEqualsFile("/scala-union-models-case-classes.txt", sourceFiles.head.contents)
        }
      }
    }

    it("generates valid readers for the union type itself") {
      val user = ssd.unions.find(_.name == "User").get
      val code = Play2Json(ssd).readers(user)
      models.TestHelper.assertEqualsFile("/scala-union-models-json-union-type-readers.txt", code)
    }

    it("generates valid writers for the union type itself") {
      val user = ssd.unions.find(_.name == "User").get
      val code = Play2Json(ssd).writers(user)
      models.TestHelper.assertEqualsFile("/scala-union-models-json-union-type-writers.txt", code)
    }

    it("codegen") {
      val code = Play2Json(ssd).generate()
      models.TestHelper.assertEqualsFile("/scala-union-models-json.txt", code)
    }
  }

  describe("enums") {

    val json = models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "models": [],
      "resources": [],

      "enums": [
        {
          "name": "member_type",
          "attributes": [],
          "plural": "member_types",
          "values": [
            { "name": "Registered", "attributes": [] },
            { "name": "Guest", "attributes": [] }
          ]
        },
        {
          "name": "role_type",
          "attributes": [],
          "plural": "role_types",
          "values": [
            { "name": "Admin", "attributes": [] }
          ]
        }
      ],

      "unions": [
        {
          "name": "user_type",
          "attributes": [],
          "plural": "user_types",
          "types": [
            { "type": "member_type", "attributes": [] },
            { "type": "role_type", "attributes": [] }
          ]
        }
      ]
    """)

    lazy val service = models.TestHelper.service(json)
    lazy val ssd = ScalaService(service)

    it("codegen") {
      val code = Play2Json(ssd).generate()
      models.TestHelper.assertEqualsFile("/scala-union-enums-json.txt", code)
    }
  }

}
