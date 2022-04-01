package circe

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import io.circe.syntax._
import io.circe.parser.decode


class JsonTest extends AnyFunSuite{

    val jsonString: Json = Json.fromString("scala exercises")
    val jsonDouble: Option[Json] = Json.fromDouble(1)
    val jsonBoolean: Json = Json.fromBoolean(true)

    val fieldList = List(("key1", Json.fromString("value1")),("key2", Json.fromInt(1)))
    val jsonFromFields: Json = Json.fromFields(fieldList)

    test("reconstructing jsons"){

        "{\"key\":\"value\"}" shouldBe Json.fromFields(List(("key", Json.fromString("value")))).noSpaces

        "{\"name\":\"sample json\",\"data\":{\"done\":false}}" shouldBe
          Json.fromFields(List(
              ("name", Json.fromString("sample json")),
              ("data", Json.fromFields(List(("done", Json.fromBoolean(false)))))
          )).noSpaces

        "[{\"x\":1}]" shouldBe Json.arr(Json.fromFields(List(("x", Json.fromInt(1))))).noSpaces

        val jsonArray: Json = Json.fromValues(List(
            Json.fromFields(List(("field1", Json.fromInt(1)))),
            Json.fromFields(List(
                ("field1", Json.fromInt(200)),
                ("field2", Json.fromString("Having circe in Scala Exercises is awesome"))))))

        def transformJson(jsonArray: Json): Json =
            jsonArray mapArray { oneJson: Vector[Json] =>
                oneJson.init
            }

        transformJson(jsonArray).noSpaces shouldBe "[{\"field1\":1}]"
    }

    test("EXTRACTING & TRANSFORMING DATA"){

        /***
         * TRANSFORMING DATA
            In this section we are going to learn how to use a cursor to modify JSON
            Circe has three slightly different cursor implementations:
                - Cursor provides functionality for moving around a tree and making modifications.
                - HCursor tracks the history of operations performed. This can be used to provide useful error messages when something goes wrong.
                - ACursor also tracks history, but represents the possibility of failure (e.g. calling downField on a field that doesn’t exist.
         * */
        import io.circe._
        import io.circe.parser._

        val json: String = """
            {
            "id": "c730433b-082c-4984-9d66-855c243266f0",
            "name": "Foo",
            "counts": [1, 2, 3],
            "values": {
                "bar": true,
                "baz": 100.001,
                "qux": ["a", "b"]
                }
            } """
        val doc: Json = parse(json).getOrElse(Json.Null)
        val cursor: HCursor = doc.hcursor
        val baz: Decoder.Result[Double] = cursor.downField("values").downField("baz").as[Double]
        baz shouldBe Right(100.001)

        val baz2: Decoder.Result[Double] = cursor.downField("values").get[Double]("baz")
        baz2 shouldBe Right(100.001)

        val secondQux: Decoder.Result[String] = cursor.downField("values").downField("qux").downArray.right.as[String]
        secondQux shouldBe Right("b")

        val nameField = cursor.downField("name").withFocus(_.mapString(_.reverse)).as[String]
        nameField shouldBe Right("ooF")
    }


    test("encoding/decoding"){
       val intsJson = List(1, 2, 3).asJson
        intsJson.as[List[Int]] shouldBe Right(List(1, 2, 3))
        // define implicits decoders and encoders
        import io.circe._, io.circe.generic.semiauto._
        case class Foo(a: Int, b: String, c: Boolean)
        implicit val fooDecoder: Decoder[Foo] = deriveDecoder[Foo]
        implicit val fooEncoder: Encoder[Foo] = deriveEncoder[Foo]

        // using JsonCodec to silently define the implicits decoder/encoder
        import io.circe.generic.JsonCodec, io.circe.syntax._
//        @JsonCodec
//        case class Bar(i: Int, s: String)
//        Bar(13, "Qux").asJson

        // override implicits decoders and encoders
        case class User(id: Long, firstName: String, lastName: String)
        object UserCodec {
            implicit val decodeUser: Decoder[User] =
                Decoder.forProduct3("id", "first_name", "last_name")(User.apply)

            implicit val encodeUser: Encoder[User] =
                Encoder.forProduct3("id", "first_name", "last_name")(u =>
                    (u.id, u.firstName, u.lastName))
        }

        // specify key decoders
        case class Boo(value: String)
        implicit val booKeyEncoder: KeyEncoder[Boo] = (boo: Boo) => boo.value
        val map = Map[Boo, Int](Boo("hello") -> 123, Boo("world") -> 456)

//        map.asJson.noSpaces shouldBe "{\"hello\":123,\"world\":456 }"
    }
}
