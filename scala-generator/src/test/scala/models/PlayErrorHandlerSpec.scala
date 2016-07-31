package scala.models

import com.bryzek.apidoc.spec.v0.models.Service
import com.bryzek.apidoc.generator.v0.models.InvocationForm
import scala.generator.ScalaService
import org.scalatest.{ ShouldMatchers, FunSpec }

class PlayErrorHandlerSpec extends FunSpec with ShouldMatchers {

  lazy val service = models.TestHelper.referenceApiService
  lazy val form = InvocationForm(service)

  it("generates play error handler") {
    val generated = PlayErrorHandler.generateCode(form)

    models.TestHelper.assertEqualsFile(
      "/generators/play-2-error-handler.txt",
      generated(0).contents // members
    )
  }

}
