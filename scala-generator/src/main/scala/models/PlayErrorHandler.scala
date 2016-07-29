package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.{Attribute, ResponseCodeInt}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil}
import scala.generator.ScalaDatatype.Container
import generator.ServiceFileNames
import play.api.libs.json.JsString

object PlayErrorHandler extends PlayErrorHandler

trait PlayErrorHandler extends CodeGenerator {

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(form: InvocationForm): Seq[File] = {
    val source = """
package handlers

import scala.concurrent._
import javax.inject.Singleton;

import play.api.http.HttpErrorHandler
import play.api.http.{Status ⇒ HttpStatus}
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Logger

@Singleton
class ErrorHandler extends HttpErrorHandler {

  private val logger = Logger(this.getClass)

  /*
   * The original JSON error message looks like:
   *
   *   Invalid Json: Unexpected end-of-input: expected close marker for OBJECT (from [Source: [B@596b69fa; line: 1, column: 3])
   *     at [Source: [B@596b69fa; line: 3, column: 80]]
   *
   * The `Source` part doesn't make sense as it just prints the underlying byte
   * array. To make it look nicer, we can remove that part so that it becomes:
   *
   *  Invalid Json: Unexpected end-of-input: expected close marker for OBJECT (from [line: 1, column: 3])
   *    at [line: 3, column: 80]
   *
   */
  def cleanupJsonError(message: String) =
    message.replaceAll("Source:.*;\\s?", "")

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    logger.warn(s"Client error handling request: [$request], status: [$statusCode], error message: [$message]")
    val msg = statusCode match {
      case HttpStatus.NOT_FOUND ⇒ "Requested resource doesn't exist"
      case _                    ⇒ cleanupJsonError(message)
    }

    Future.successful(
      Status(statusCode)(Json.toJson(Error(statusCode.toString, msg)))
    )
  }

  def onServerError(request: RequestHeader, ex: Throwable) = {
    logger.error(s"Unexpected error handling request: [$request]", ex)
    Future.successful(
      InternalServerError(Json.toJson(Error("500", "Unexpected server error, please try again")))
    )
  }
}
"""
    Seq(File("ErrorHandler.scala", Some("handlers"), source))
  }

}
