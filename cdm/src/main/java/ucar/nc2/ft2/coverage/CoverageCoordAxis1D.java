/* Copyright */
package ucar.nc2.ft2.coverage;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.constants.AxisType;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.util.Indent;
import ucar.nc2.util.NamedAnything;
import ucar.nc2.util.NamedObject;
import ucar.unidata.util.Format;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * Coverage CoordAxis 1D case
 *
 * @author caron
 * @since 7/15/2015
 */
public class CoverageCoordAxis1D extends CoverageCoordAxis {

  // subset ??
  protected int minIndex, maxIndex; // closed interval [minIndex, maxIndex] ie minIndex to maxIndex are included, nvalues = max-min+1.
  protected int stride = 1;

  public CoverageCoordAxis1D(String name, String units, String description, DataType dataType, AxisType axisType, List<Attribute> attributes,
                                DependenceType dependenceType, List<String> dependsOn, Spacing spacing, int ncoords, double startValue, double endValue,
                                double resolution, double[] values, CoordAxisReader reader) {

    super(name, units, description, dataType, axisType, attributes, dependenceType, dependsOn, spacing, ncoords, startValue, endValue, resolution, values, reader);

    this.minIndex = 0;
    this.maxIndex = ncoords-1;
  }

  public CoverageCoordAxis1D copy() {
    return new CoverageCoordAxis1D(name, units, description, dataType, axisType, attributes.getAttributes(), dependenceType, dependsOn, spacing, ncoords, startValue, endValue, resolution, values, reader);
  }

  public int getStride() {
    return stride;
  }

  // for subsetting - these are the indexes reletive to original - note cant compose !!
  void setIndexRange(int minIndex, int maxIndex, int stride) {
    this.minIndex = minIndex;
    this.maxIndex = maxIndex;
    this.stride = stride;
  }

  public int getMinIndex() {
    return minIndex;
  }

  public int getMaxIndex() {
    return maxIndex;
  }

  @Override
  public void toString(Formatter f, Indent indent) {
    super.toString(f, indent);
    f.format("%s   minIndex=%d maxIndex=%d stride=%d%n", indent, minIndex, maxIndex, stride);
  }


  ///////////////////////////////////////////////////////////////////
  // Spacing

  /*
   * regular: regularly spaced points or intervals (start, end, npts), edges halfway between coords
   * irregularPoint: irregular spaced points (values, npts), edges halfway between coords
   * contiguousInterval: irregular contiguous spaced intervals (values, npts), values are the edges, and there are npts+1, coord halfway between edges
   * discontinuousInterval: irregular discontiguous spaced intervals (values, npts), values are the edges, and there are 2*npts: low0, high0, low1, high1...
   */

  public boolean isAscending() {
    switch (spacing) {
      case regular:
        return getResolution() > 0;

      case irregularPoint:
        return values[0] <= values[ncoords - 1];

      case contiguousInterval:
        return values[0] <= values[ncoords];

      case discontiguousInterval:
        return values[0] <= values[2*ncoords-1];
    }
    throw new IllegalStateException("unknown spacing"+spacing);
  }

  public double getCoordMidpoint(int index) {
    switch (spacing) {
      case regular:
      case irregularPoint:
        return getCoord(index);

      case contiguousInterval:
      case discontiguousInterval:
        return (getCoordEdge1(index)+getCoordEdge2(index))/2;
    }
    throw new IllegalStateException("Unknown spacing=" + spacing);
  }

  public double getCoord(int index) {
    switch (spacing) {
      case regular:
        if (index < 0 || index >= ncoords) throw new IllegalArgumentException("Index out of range " + index);
        return startValue + index * getResolution();

      case irregularPoint:
        return values[index];

      case contiguousInterval:
        return (values[index] + values[index + 1]) / 2;

      case discontiguousInterval:
        return (values[2 * index] + values[2 * index + 1]) / 2;
    }
    throw new IllegalStateException("Unknown spacing=" + spacing);
  }

  public double getCoordEdge1(int index) {
    switch (spacing) {
      case regular:
        if (index < 0 || index >= ncoords) throw new IllegalArgumentException("Index out of range " + index);
        return startValue + (index - .5) * getResolution();

      case irregularPoint:
        if (index > 0)
          return (values[index - 1] + values[index]) / 2;
        else
          return values[0] - (values[1] - values[0]) / 2;

      case contiguousInterval:
        return values[index];

      case discontiguousInterval:
        if (values == null || index <0)
          System.out.println("HEY");
        return values[2 * index];
    }
    throw new IllegalStateException("Unknown spacing=" + spacing);
  }

  public double getCoordEdge2(int index) {
    switch (spacing) {
      case regular:
        if (index < 0 || index >= ncoords) throw new IllegalArgumentException("Index out of range " + index);
        return startValue + (index + .5) * getResolution();

      case irregularPoint:
        if (index < ncoords - 1)
          return (values[index] + values[index + 1]) / 2;
        else
          return values[index] + (values[index] - values[index - 1]) / 2;

      case contiguousInterval:
        return values[index + 1];

      case discontiguousInterval:
        return values[2 * index + 1];
    }
    throw new IllegalStateException("Unknown spacing=" + spacing);
  }

  public double getCoordEdgeLast() {
    return getCoordEdge2( ncoords - 1);
  }

  @Override
  public Array getCoordsAsArray() {
    getValues();
    Array result;
    switch (dependenceType) {
      case scalar:
        result = Array.factory(getDataType(), new int[0]);
        break;
      default:
        result = Array.factory(getDataType(), new int[] { ncoords});
        break;
    }

    for (int i=0; i< ncoords; i++)
      result.setDouble(i, getCoord(i));
    return result;
  }

  @Override
  public Array getCoordBoundsAsArray() {
    getValues();
    Array result = Array.factory(getDataType(), new int[] { ncoords, 2});

    int count = 0;
    for (int i=0; i<ncoords; i++) {
      result.setDouble(count++, getCoordEdge1(i));
      result.setDouble(count++, getCoordEdge2(i));
    }
    return result;
  }

  @Override
  public CoverageCoordAxis subset(double minValue, double maxValue) {
    CoordAxisHelper helper = new CoordAxisHelper(this);
    return helper.subset(minValue, maxValue);
  }

 /* public Array getCoordEdge1() {
    getValues();
    double[] vals = new double[ ncoords];
    for (int i=0; i< ncoords; i++)
      vals[i] = getCoordEdge1(i);
    return Array.makeFromJavaArray(vals);
  }

  public Array getCoordEdge2() {
    getValues();
    double[] vals = new double[ ncoords];
    for (int i=0; i< ncoords; i++)
      vals[i] = getCoordEdge2(i);
    return Array.makeFromJavaArray(vals);
  } */

  public List<NamedObject> getCoordValueNames() {
    getValues();  // read in if needed
    if (timeHelper != null) {
      return timeHelper.getCoordValueNames(this);
    }

    List<NamedObject> result = new ArrayList<>();
    for (int i = 0; i < ncoords; i++) {
      Object value = null;
      switch (spacing) {
        case regular:
        case irregularPoint:
          value = Format.d(getCoord(i), 3);
          break;

        case contiguousInterval:
        case discontiguousInterval:
          value = new CoordInterval(getCoordEdge1(i), getCoordEdge2(i), 3);
          break;
      }
      result.add(new NamedAnything(value, value + " " + getUnits()));
    }

    return result;
  }

    // LOOK  incomplete handling of subsetting params
  // create a copy of the axis, with the values subsetted by the params as needed
  @Override
  public CoverageCoordAxis subset(SubsetParams params) {
    CoordAxisHelper helper = new CoordAxisHelper(this);

    switch (getAxisType()) {
      case GeoZ:
      case Pressure:
      case Height:
        Double dval = params.getDouble(SubsetParams.vertCoord);
        if (dval != null) {
          // LOOK problems when vertCoord doesnt match any coordinates in the axes
          // LOOK problems when vertCoord is discontinuous interval
          return helper.subsetClosest(dval);
        }
        break;

      case Ensemble:
         Double eval = params.getDouble(SubsetParams.ensCoord);
         if (eval != null) {
           return helper.subsetClosest(eval);
         }
         break;

       // x,y gets seperately subsetted
      case GeoX:
      case GeoY:
      case Lat:
      case Lon:
        return null;

      case Time:
        if (params.isTrue(SubsetParams.allTimes))
          return this.copy();
        if (params.isTrue(SubsetParams.latestTime))
          return helper.subsetLatest();

        CalendarDate date = (CalendarDate) params.get(SubsetParams.time);
        if (date != null)
          return helper.subset(date);

        CalendarDateRange dateRange = (CalendarDateRange) params.get(SubsetParams.timeRange);
        if (dateRange != null)
          return helper.subset(dateRange);
        break;

      case RunTime:
        if (params.isTrue(SubsetParams.latestRuntime))
          return helper.subsetLatest();

        CalendarDate rundate = (CalendarDate) params.get(SubsetParams.runtime);
        if (rundate != null)
          return helper.subset(rundate);

        CalendarDateRange rundateRange = (CalendarDateRange) params.get(SubsetParams.runtimeRange);
        if (rundateRange != null)
          return helper.subset(rundateRange);
        break;

    }

    // otherwise take the entire axis
    return this.copy();
  }

}

