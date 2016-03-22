package ta

import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.rdd.RDD

import scala.collection.mutable.ArrayBuffer
import scala.util.{Try, Success, Failure}
import org.apache.spark.util

class Clustering {
  val archetipi = """0;resident;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0;1.0
  1;resident;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5;0.5
  2;resident; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1
  3;dynamic_resident;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0;1.0;1.0;1.0
  4;dynamic_resident;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0;0.5;0.5;0.5
  5;dynamic_resident;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0; 0.1; 0.1; 0.1
  5;commuter;1.0;1.0;1.0;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0
  6;commuter;0.5;0.5;0.5;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0
  7;commuter; 0.1; 0.1; 0.1;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0
  8;visitor;1.0;1.0;1.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0
  9;visitor;0.5;0.5;0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0
  10;visitor; 0.1; 0.1; 0.1;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0
  11;resident;1.0;1.0;1.0;1.0;1.0;1.0;0.5;0.5;0.5;0.5;0.5;0.5; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1;0.0;0.0;0.0;0.0;0.0;0.0
  12;resident;0.5;0.5;0.5;0.5;0.5;0.5; 0.1; 0.1; 0.1; 0.1; 0.1; 0.1;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0
  13;visitor;0.0;0.0;0.0;1.0;1.0;1.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0
  14;visitor;0.0;0.0;0.0;0.5;0.5;0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0
  15;visitor;0.0;0.0;0.0; 0.1; 0.1; 0.1;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0"""

  def parseArchetipi(s: String = archetipi, vLen: Int = 18) = {
    archetipi.split("\n", -1).map(l => {
      val w = l.split(";", -1).map(_.trim)
      Vectors.dense(w.slice(2, vLen + 2).map(_.toDouble)) -> w(1)
    }).toMap
  }

  /** split into two clusters using kmeans **/
  def split(cluster: RDD[Vector], subIterations: Int) = {
    Try(
      KMeans.train(cluster, k = 2, maxIterations = subIterations)
    )
  }

  def divise(data: RDD[Vector], clusterNum: Int, subIterations: Int) = {
    var clusters = Seq((Vectors.zeros(data.first.size), data)).par
    while (clusters.size < clusterNum) {
      clusters = clusters.flatMap{ case (c, d) => {
        split(d, subIterations) match {
          case Success(model) =>
            model.clusterCenters.zipWithIndex.map{
              case(cntr, idx) => (cntr, (d.filter(x => model.predict(x) == idx)))
            }.par
          case Failure(f) =>
            Seq((c, d)).par
        }
      }}
    }
    // keep only the centers
    clusters.map(_._1)
  }

  def run(data: RDD[Vector], clusterNum: Int, subIterations: Int) = {
    val centers = divise(data, clusterNum, subIterations)

    val archetipiMap = parseArchetipi(vLen = data.first.size)
    val tipiCentroidi = centers.map( ctr => {
      val min_ = archetipiMap.keys.reduceLeft( (a, b) => if (euclidean(ctr, a) < euclidean(ctr, b)) a else b)
      (archetipiMap(min_), ctr)
    })
    tipiCentroidi
  }

  def euclidean(v1: Vector, v2: Vector) = {
    val uv1 = util.Vector(v1.toArray)
    val uv2 = util.Vector(v2.toArray)
    math.sqrt(uv1.squaredDist(uv2))
  }
}

object Clustering extends Clustering {
  def main(args: Array[String]) {
    val appName = this.getClass().getSimpleName
    val master = Try{args(0)}.getOrElse("spark://localhost:7077")
    val region = args(1)
    val timeframe = args(2)
    val clusterNum = Try{args(3).toInt}.getOrElse(100)
    val subIterations = Try{args(4).toInt}.getOrElse(5)
    val conf = new SparkConf()
      .setAppName(appName)
      .setMaster(master)
    val sc = new SparkContext(conf)

    val pattern = """^\[(.*)\]$""".r
    val data = sc.textFile(s"/r_carrelli-${region}-${timeframe}").map{
      case pattern(s) =>
        Vectors.dense(s.split(",", -1).map(_.trim.toDouble))
    }.cache

    val tipiCentroidi = run(data, clusterNum, subIterations)

    sc.parallelize(tipiCentroidi.toList.toSeq).saveAsTextFile(s"/centroids-${region}-${timeframe}")
    sc.stop
  }
}