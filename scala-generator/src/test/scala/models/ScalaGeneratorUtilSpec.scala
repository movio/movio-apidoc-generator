package scala.models

import lib.Datatype
import com.bryzek.apidoc.spec.v0.models.{Method, Model, Parameter, ParameterLocation, Operation, Resource}
import scala.generator._
import org.scalatest.{ ShouldMatchers, FunSpec }

class ScalaGeneratorUtilSpec extends FunSpec with ShouldMatchers {

  private lazy val service = models.TestHelper.referenceApiService
  private lazy val ssd = new ScalaService(service)

  private[this] val play2Util = ScalaGeneratorUtil(
    ScalaClientMethodConfigs.Play24("test.apidoc")
  )

  describe("params") {
    val model = new Model("model", "models", None, None, Nil)
    val q1 = new Parameter(
      "q1",
      "double",
      ParameterLocation.Query,
      None, None, true, None, None, None, None
    )
    val q2 = new Parameter(
      "q2",
      "double",
      ParameterLocation.Query,
      None, None, false, None, None, None, None
    )
    val operation = new Operation(Method.Get, "/models", None, None, None, Seq(q1, q2), Nil)
    val resource = new Resource(model.name, model.plural, None, None, None, Seq(operation))

    it("should handle required and non-required params") {
      val scalaModel = new ScalaModel(ssd, model)
      val code = play2Util.queryParameters(
        "queryParameters",
        new ScalaOperation(
          ssd,
          operation,
          new ScalaResource(ssd, resource)
        ).queryParameters
      )
      code.get should equal("""val queryParameters = Seq(
  Some("q1" -> q1.toString),
  q2.map("q2" -> _.toString)
).flatten""")
    }
  }


  it("should generate a valid URI for matrix parameters") {
    val params = Seq(
      Parameter(name = "x", `type` = "string", location = ParameterLocation.Path, required = true),
      Parameter(name = "y", `type` = "string", location = ParameterLocation.Path, required = true),
      Parameter(name = "z", `type` = "string", location = ParameterLocation.Query, required = true)
    )

    val operation = new Operation(
      method = Method.Get,
      path = "/foo/x=:x;y=:y?z=:z",
      parameters = params
    )

    val resource = {
      val model = new Model("", "", None, None, Nil)
      val r = new Resource("", "", None, None, None, Nil)
      new ScalaResource(ssd, r)
    }

    val rendered = play2Util.pathParams(new ScalaOperation(ssd, operation, resource))

    val expected = (
        """ s"/foo/""" +
          """x=${play.utils.UriEncoding.encodePathSegment(x, "UTF-8")};""" +
          """y=${play.utils.UriEncoding.encodePathSegment(y, "UTF-8")}?""" +
          """z=:z" """
      ).trim

    rendered shouldBe expected
  }

  it("supports query parameters that contain lists") {
    val operation = ssd.resources.find(_.plural == "Echoes").get.operations.head
    val code = play2Util.queryParameters("queryParameters", operation.queryParameters).get
    code should be("""
val queryParameters = Seq(
  foo.map("foo" -> _)
).flatten ++
  optionalMessages.getOrElse(Nil).map("optional_messages" -> _) ++
  requiredMessages.map("required_messages" -> _)
""".trim)
  }

  it("supports query parameters that ONLY have lists") {
    val operation = ssd.resources.find(_.plural == "Echoes").get.operations.find(_.path == "/echoes/arrays-only").get
    val code = play2Util.queryParameters("queryParameters", operation.queryParameters).get
    code should be("""
val queryParameters = optionalMessages.getOrElse(Nil).map("optional_messages" -> _) ++
  requiredMessages.map("required_messages" -> _)
""".trim)
  }

  it("supports optional seq query parameters") {
    val operation = ssd.resources.find(_.plural == "Users").get.operations.find(op => op.method == Method.Get && op.path == "/users").get

    models.TestHelper.assertEqualsFile(
      "/generators/play-2-route-util-reference-get-users.txt",
      play2Util.queryParameters("queryParameters", operation.queryParameters).get
    )
  }

}
