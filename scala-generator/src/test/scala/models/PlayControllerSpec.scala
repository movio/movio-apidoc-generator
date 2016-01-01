package scala.models

import com.bryzek.apidoc.spec.v0.models.Service
import com.bryzek.apidoc.generator.v0.models.InvocationForm
import scala.generator.ScalaService
import org.scalatest.{ ShouldMatchers, FunSpec }

class PlayControllerSpec extends FunSpec with ShouldMatchers {

  lazy val service = models.TestHelper.referenceApiService
  lazy val form = InvocationForm(service)

  it("generates play controller") {
    models.TestHelper.assertEqualsFile(
      "/generators/play-2-controller-full.txt",
      PlayController.generateCode(form).head.contents
    )
  }

}
