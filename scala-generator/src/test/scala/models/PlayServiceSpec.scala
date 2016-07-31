package scala.models

import com.bryzek.apidoc.generator.v0.models.InvocationForm
import org.scalatest.{ ShouldMatchers, FunSpec }

class PlayServiceSpec extends FunSpec with ShouldMatchers {

  lazy val service = models.TestHelper.referenceApiService
  lazy val form = InvocationForm(service)

  it("generates play controller") {
    models.TestHelper.assertEqualsFile(
      "/generators/play-2-service-full.txt",
      PlayService.generateCode(form)(1).contents
    )
  }

}
