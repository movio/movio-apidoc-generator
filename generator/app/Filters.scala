import javax.inject.Inject
import play.api.http.HttpFilters

class Filters @Inject() (
  log: LoggingFilter
) extends HttpFilters {
  val filters = Seq(log)
}
