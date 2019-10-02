package latis.data

import latis.metadata._
import latis.model._

/**
 * Define a one-dimensional linear (regularly spaced) domain set 
 * where each domain value represents a bin center. 
 * The bins have the same size and are contiguous.
 */
class BinSet1D(
  start: Double, increment: Double, count: Int
) extends LinearSet1D(start, increment, count) with Serializable {
  
  /**
   * Override to extend min coverage by half a bin width.
   */
  override val min = super.min match {
    //TODO: map over DomainData?
    case DomainData(Number(x)) => DomainData(x - (increment/2))
  }
  
  /**
   * Override to extend max coverage by half a bin width.
   */
  override val max = super.min match {
    case DomainData(Number(x)) => DomainData(x + (increment/2))
  }
  
  /**
   * Override to match any value that falls within a bin.
   */  
  override def indexOf(data: DomainData): Int = {
    val index = data match {
      case DomainData(Number(x)) => 
        ((x - start)/increment + 0.5).toInt //bin centered semantics
    }
    if (index >= 0 && index < length) index
    else -1
  }
}

object BinSet1D {
  
  def apply(start: Double, increment: Double, count: Int): BinSet1D =
    new BinSet1D(start, increment, count)
  
  def fromExtents(min: Double, max: Double, count: Int): BinSet1D = {
    //TODO: ensure max > min
    val start = min
    val increment = ((max - min) / count) //bin semantics
    new BinSet1D(start, increment, count)
  }
  
  def fromExtents(min: Double, max: Double, increment: Double): BinSet1D = {
    //TODO: ensure max > min
    val count = ((max - min) / increment).toInt //will round down
    new BinSet1D(min, increment, count)
  }
    
}