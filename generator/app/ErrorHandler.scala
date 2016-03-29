import com.bryzek.apidoc.generator.v0.models.json._
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent._
import lib.Validation
import play.api.libs.json._
import play.api._

class ErrorHandler extends HttpErrorHandler {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    Future.successful(BadRequest(Json.toJson(Validation.serverError("Bad Request"))))
  }

  def onServerError(request: RequestHeader, ex: Throwable) = {
    Logger.error(ex.toString, ex)
    Future.successful(InternalServerError(Json.toJson(Validation.serverError())))
  }

}
