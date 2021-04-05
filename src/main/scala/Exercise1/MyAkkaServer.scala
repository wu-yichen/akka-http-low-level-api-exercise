package Exercise1

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object MyAkkaServer extends App {
  implicit val system: ActorSystem = ActorSystem("MyAkkaServer")
  implicit val materilizer: ActorMaterializer.type = ActorMaterializer
  import system.dispatcher

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), value, entity, protocol) => Future {
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   this is about page
            | </body>
            |</html>
            |""".stripMargin
        )
      )}
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), value, entity, protocol) => Future {
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Welcome to my AKKA HTTP Server !!
            | </body>
            |</html>
            |""".stripMargin
        )
      )}
    case request: HttpRequest => Future {
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/plain(UTF-8)`,
          "not found"
        )
      )}
  }
 // Http().bindAndHandleSync(requestHandler, "localhost", 8388)
   val bindingFuture = Http().newServerAt("localhost", 8388).bind(requestHandler)

  // shutdown server
  bindingFuture.flatMap(binding => binding.unbind()).onComplete(_ => system.terminate())
}
