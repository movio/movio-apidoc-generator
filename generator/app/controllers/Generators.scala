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

  def findGenerator(key: String): Option[(CodeGenTarget, CodeGenerator)] = for {
    target <- targets.find(_.metaData.key == key)
    codeGenerator <- target.codeGenerator
  } yield(target -> codeGenerator)

  val targets = Seq(
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_4_client",
          name = "Play 2.4 client",
          description = Some("Play Framework 2.4 client based on <a href='http://www.playframework.com/documentation/2.4.x/ScalaWS'>WS API</a>. Primary change from 2.3.x is WSRequestHolder has been deprecated (replaced by WSRequest)."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play24ClientGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_x_json",
          name = "Play 2.x json",
          description = Some("Generate play 2.x case classes with json serialization based on <a href='http://www.playframework.com/documentation/2.3.x/ScalaJsonCombinators'>Scala Json combinators</a>. No need to use this target if you are already using the Play Client target."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play2Models)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_x_routes",
          name = "Play 2.x routes",
          description = Some("""Generate a routes file for play 2.x framework. See <a href="/doc/playRoutesFile">Play Routes File</a>."""),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play2RouteGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "scala_models",
          name = "Scala models",
          description = Some("Generate scala models from the API description."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.generator.ScalaCaseClasses)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "movio_scala_models",
          name = "Movio Scala Models",
          description = Some("Validated Scala Models"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.AdvancedCaseClasses)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_x_json_standalone",
          name = "Play 2.x json_standalone",
          description = Some("Generate play 2.x json serialization based on <a href='http://www.playframework.com/documentation/2.3.x/ScalaJsonCombinators'>Scala Json combinators</a>."),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Play2JsonStandalone)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "kafka_0_8",
          name = "Kafka Consumer / Producer",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.Kafka)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "kafka_0_8_tests",
          name = "Kafka Tests",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.KafkaTests)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_app_services",
          name = "Play Services",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlayService)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_app_controllers",
          name = "Play Controllers",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlayController)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_app_tests",
          name = "Play Tests",
          description = Some("TBC"),
          language = Some("Scala")
        ),
        status = lib.generator.Status.Beta,
        codeGenerator = Some(scala.models.PlaySystemTests)
      )
  ).sortBy(_.metaData.key)
}
