package scala.generator

object ScalaClientCommon {

  def apply(
    config: ScalaClientMethodConfig
  ): String = {
    Seq(
      ScalaClientObject(config),
      ScalaClientAuthClassses()
    ).mkString("\n\n")
  }

  def clientSignature(
    config: ScalaClientMethodConfig
  ): String = {

    val executorService = config.requiresAsyncHttpClient match {
      case true => ",\n  asyncHttpClient: AsyncHttpClient = Client.defaultAsyncHttpClient"
      case false => ""
    }

    s"""
/** Play Client
  *
  * For details on config see - https://www.playframework.com/documentation/2.4.x/ScalaWS
  *
  * @param apiUrl the server/host/port to connect to, eg `http://localhost:9000`
  * @param auth if auth is used
  * @param defaultHeaders to be sent with all requests
  * @param autoClose if the client is used within a Play App this setting isn't used. Play will manage
  *        the settings and client. If used outside a Play app a client with settings must be created.
  *        `autoClose = true` means that a new client will be created for every request, this includes
  *        parsing the configuration. The client will close the connection for you.
  *        If `autoClose = false` one connnection will be made when the class is instantiated. You are
  *        responsible for closing the connection.
  */
class Client(
  apiUrl: String,
  auth: scala.Option[${config.namespace}.Authorization] = None,
  defaultHeaders: Seq[(String, String)] = Nil,
  autoClose: Boolean = true // Set to false for better perf or lots or request. You must manually call `close`$executorService
)
""".trim
  }

}
