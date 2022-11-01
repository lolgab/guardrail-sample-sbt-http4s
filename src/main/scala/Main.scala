import cats.syntax.all._
import cats.effect._
import epollcat.EpollApp

object Main extends EpollApp {

  def run(args: List[String]): IO[ExitCode] = {
    Server.server.use(_ => IO.never).as(ExitCode.Success)
  }

}

