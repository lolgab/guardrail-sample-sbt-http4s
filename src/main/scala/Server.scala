import cats.{Order => _, _}
import cats.syntax.all._
import cats.effect._

import org.http4s.implicits._

import example.server.definitions._
import example.server.store._
import scala.concurrent.duration._
import snunit.http4s._

object Server {

  def server: Resource[IO, Unit] = for {
    // Shared State
    inventory <- Resource.eval(
      Ref[IO].of(Map[String, Int]( // Initial Inventory
        "Kibble" -> 10,
        "Treats" -> 3
      ))
    )
    orders <- Resource.eval(
      Ref[IO].of(Map[Long, Order]( // Initial Orders
        123L -> Order(id = Some(123L), petId = Some(5L), quantity = Some(3), status = Some(Order.Status.Placed))
      ))
    )

    // Generate Server
    _ <- SNUnitServerBuilder.default[IO]
      .withHttpApp(new StoreResource[IO]().routes(Server.handler(inventory, orders)).orNotFound) // Server
      .build
  } yield ()

  def handler[F[_]: Sync](inventory: Ref[F, Map[String, Int]], orders: Ref[F, Map[Long, Order]]) = new StoreHandler[F]{

    def getInventory(respond: StoreResource.GetInventoryResponse.type)(): F[StoreResource.GetInventoryResponse] =
      inventory.get.map(respond.Ok(_))

    def deleteOrder(respond: StoreResource.DeleteOrderResponse.type)(orderId: Long): F[StoreResource.DeleteOrderResponse] =
      orders.modify{m =>
        val out = m.get(orderId).fold[StoreResource.DeleteOrderResponse](respond.NotFound)(_ => respond.Accepted)
        (m - orderId, out)
      }

    def getOrderById(respond: StoreResource.GetOrderByIdResponse.type)(orderId: Long): F[StoreResource.GetOrderByIdResponse] =
      orders.get
        .map(_.get(orderId))
        .map(_.fold[StoreResource.GetOrderByIdResponse](respond.NotFound)(o => respond.Ok(o)))

    // Weird that this is optional
    def placeOrder(respond: StoreResource.PlaceOrderResponse.type)(body: Order): F[StoreResource.PlaceOrderResponse] =
      for {
        id <- body.id.fold(Sync[F].delay(scala.util.Random.nextLong))(_.pure[F])
        newOrder = body.copy(id = id.some)
        _ <- orders.update(m => m + (id -> newOrder))
      } yield respond.Ok(newOrder)
  }

}
