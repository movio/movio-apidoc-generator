package controllers

import com.bryzek.apidoc.generator.v0.models.Generator
import com.bryzek.apidoc.generator.v0.models.json._
import play.api.mvc._
import play.api.libs.json._
import lib.generator.{CodeGenerator, CodeGenTarget}

class Generators extends Controller {

  def get(
    key: Option[String] = None,
    limit: Integer = 100,
    offset: Integer = 0
  ) = Action { request: Request[AnyContent] =>
    val generators = Generators.targets.
      filter(t => t.codeGenerator.isDefined && t.status != lib.generator.Status.Proposal).
      filter(t => key.isEmpty || key == Some(t.metaData.key)).
      map(t => t.metaData)

    Ok(Json.toJson(generators.drop(offset).take(limit)))
  }

  def getByKey(key: String) = Action { request: Request[AnyContent] =>
    Generators.findGenerator(key) match {
      case Some((target, _)) => Ok(Json.toJson(target.metaData))
      case _ => NotFound
    }
  }

}

object Generators {
  val prefix = ""

  def findGenerator(key: String): Option[(CodeGenTarget, CodeGenerator)] = for {
    target <- targets.find(_.metaData.key == key)
    codeGenerator <- target.codeGenerator
  } yield(target -> codeGenerator)

  val targets = Seq(
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_2_4_client",
          name = s"Play 2.4 client${prefix}",
          description = Some("Play Framework 2.4 client based on <a href='http://www.playframework.com/documentation/2.4.x/ScalaWS'>WS API</a>. Primary change from 2.3.x is WSRequestHolder has been deprecated (replaced by WSRequest)."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play24ClientGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_2_x_json",
          name = s"Play 2.x json${prefix}",
          description = Some("Generate play 2.x case classes with json serialization based on <a href='http://www.playframework.com/documentation/2.3.x/ScalaJsonCombinators'>Scala Json combinators</a>. No need to use this target if you are already using the Play Client target."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play2Models)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_2_x_routes",
          name = s"Play 2.x routes${prefix}",
          description = Some("""Generate a routes file for play 2.x framework. See <a href="/doc/playRoutesFile">Play Routes File</a>."""),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play2RouteGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}scala_models",
          name = s"Scala models${prefix}",
          description = Some("Generate scala models from the API description."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.generator.ScalaCaseClasses)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}advanced_scala_models",
          name = s"Advanced Scala Models${prefix}",
          description = Some("Validated Scala Models"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.AdvancedCaseClasses)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_2_x_json_standalone",
          name = s"Play 2.x json_standalone${prefix}",
          description = Some("Generate play 2.x json serialization based on <a href='http://www.playframework.com/documentation/2.3.x/ScalaJsonCombinators'>Scala Json combinators</a>."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play2JsonStandalone)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}kafka_0_8",
          name = s"Kafka Consumer / Producer${prefix}",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Kafka)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}kafka_0_8_tests",
          name = s"Kafka Tests${prefix}",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.KafkaTests)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_app_services",
          name = s"Play Services${prefix}",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlayService)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_app_controllers",
          name = s"Play Controllers${prefix}",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlayController)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_app_module",
          name = s"Play Application Module${prefix}",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlayApplicationModule)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}play_app_tests",
          name = s"Play Tests${prefix}",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlaySystemTests)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}samza_serde",
          name = s"Samza Serde${prefix}",
          description = Some("Samza Serde adapters around Play JSON serdes. Requires Advanced Scala Models and Play JSON Standalone."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.SamzaSerde)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}samza_serde_test",
          name = s"Samza Serde Test${prefix}",
          description = Some("Basic round trip test for Samza serdes. Requires Samza Serde."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.SamzaSerdeTest)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}kafka_0_9_serde",
          name = s"Kafka 0.9 Serde${prefix}",
          description = Some("Kafka Serde adapters around Play JSON serdes. Requires Advanced Scala Models and Play JSON Standalone."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Kafka09Serde)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}kafka_0_9_serde_test",
          name = s"Kafka 0.9 Serde Test${prefix}",
          description = Some("Basic round trip test for Kafka 0.9 serdes. Requires Kafka 0.9 Serde."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Kafka09SerdeTest)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = s"${prefix}scalacheck_arbitrary",
          name = s"ScalaCheck Arbitrary${prefix}",
          description = Some("Arbitrary instances for case classes and enums for use with ScalaCheck. Requires Advanced Scala Models and static classes in generator-apidoc-libs."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.ScalaCheckArbitrary)
      )
  ).sortBy(_.metaData.key)
}
