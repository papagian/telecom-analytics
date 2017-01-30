import org.apache.spark._
import org.apache.spark.SparkContext._

object Presence {
  def main(args: Array[String]) = {
      val appName = this.getClass().getSimpleName
      val usage = (s"Usage: submit.sh ${appName} <master> <input>")

      if (args.length != 2) {
          System.err.println(usage)
          System.exit(1)
      }

     val master = args(0)
     val input = args(1)

     val conf = new SparkConf().setAppName(appName).setMaster(master)
        .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrationRequired","false")
        .registerKryoClasses(Array(classOf[Array[Double]],classOf[Array[Int]]))

    val sc = new SparkContext()
/*
    val pattern = """\(\(u'(\S*)', '(\S)'\), '(dynamic_resident|visitor|resident|commuter|passing\ by)'\)""".r
    val s1 = "((u'107451309044480586', '2'), 'dynamic_resident')"
    val s2 = "((u'92863552200163866', '4'), 'passing by')"
    val s3 = "((u'29917139386874174', '5'), 'commuter')"

    val user2label = "/annotation_global/forth"
*/
    val pattern = """\(u'(\S*)', '(\S)', '(dynamic_resident|visitor|resident|commuter|passing\ by)'\)""".r
/*
    val s1 = "(u'106541285272914564', '1', 'passing by')"
    val s2 = "(u'205799597446887730', '3', 'commuter')"
    val s3 = "(u'135520406994217194', '1', 'dynamic_resident')"

    s1 match {case pattern(user, region, user_class) => print("ole")}
    s2 match {case pattern(user, region, user_class) => print("ole")}
    s3 match {case pattern(user, region, user_class) => print("ole")}
*/
    val user2label = "hdfs://sith0:9000/user/spapagian/user2label.csv"
    val user_annotation = sc.textFile(user2label).map{
        case pattern(user, region, user_class) => ((user, region), user_class)
    }

    val userCache = scala.collection.mutable.HashMap.empty[(String, String), String]

    def getUserClass(user: String, region: String): String = {
        // println(">>> getUserClass: " + user + ", "dd + region)
        val label = userCache.getOrElse(
            (user, region),
            {
                val results = user_annotation.filter{ case ((u, r), _) => u == user && r == region }.collect
                results.length match {
                    case 0 => "not classified"
                    case _ => results(0)._2
                }
            }
        )
        // println("<<< getUserClass: " + user + ", " + region + ", " + label)
        userCache((user, region)) = label
        label
    }
/*
    getUserClass("29917139386874174", "5")
    getUserClass("107451309044480586", "2")
    println("<<<" + userCache)
*/
    def getClass(users: Set[String], region: String) = {
        val rest = users.filter( u => !(userCache contains (u, region)) )
        val results = user_annotation
            .filter{ case ((u, r), _) => rest.contains(u) && r == region }
            .collect
            .toMap
        userCache ++= results
        userCache ++= rest.filter(u => !results.contains((u, region)))
            .map(u => (u, region) -> "not classified").toMap
    }

//    getClass(Set("29917139386874174", "107451309044480586", "invalid"), "5")

    val interestRegion = "hdfs://sith0:9000/user/spapagian/aree_roma.csv"

    val sites2zones  = sc.textFile(interestRegion).map(_.split(";")).map{
        case a => (a(0).substring(0, 5), a(1))}.collectAsMap.toMap

    val field2col: Map[String,Int] =
        Map("user_id" -> 0, "cdr_type" -> 11, "start_cell" -> 9 ,"end_cell"->10 , "date" -> 3 ,"time"-> 4)

    println("Input:" + input)
    val userCallsPerDate  = sc.textFile(input)
        .map( line => CDR.from_string(line, field2col) )
        .filter ( cdr => cdr.valid_region(sites2zones) )
        .map( cdr => (sites2zones(cdr.site), cdr.date, cdr.user_id) )
        .distinct
        .collect
        .map{ case (zone, date, user_id) => ((zone, date, getUserClass(user_id, zone)), 1) }

    val results = sc.parallelize(userCallsPerDate)
        .reduceByKey( _ + _ )
    results.saveAsTextFile("file:///archive/users/spapagian/asap/data/presence_timeseries.csv")
  }
}
