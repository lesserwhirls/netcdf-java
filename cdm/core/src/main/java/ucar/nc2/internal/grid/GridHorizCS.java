package ucar.nc2.internal.grid;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.constants.AxisType;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.grid.GridAxis;
import ucar.nc2.grid.GridAxis1D;
import ucar.nc2.grid.GridAxis2D;
import ucar.nc2.grid.GridHorizCoordinateSystem;
import ucar.nc2.util.MinMax;
import ucar.unidata.geoloc.*;
import ucar.unidata.geoloc.projection.LatLonProjection;
import ucar.unidata.geoloc.projection.sat.Geostationary;
import ucar.unidata.geoloc.projection.sat.MSGnavigation;
import ucar.unidata.geoloc.projection.sat.VerticalPerspectiveView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GridHorizCS implements GridHorizCoordinateSystem {

  public static GridHorizCS create(GridAxis xaxis, GridAxis yaxis, @Nullable Projection projection) {
    // WRF NMM
    String horizStaggerType = xaxis.attributes().findAttributeString(_Coordinate.Stagger, null);

    if (xaxis instanceof GridAxis1D && yaxis instanceof GridAxis1D) {
      return new GridHorizCS((GridAxis1D) xaxis, (GridAxis1D) yaxis, projection, horizStaggerType);
    } else if (xaxis instanceof GridAxis2D && yaxis instanceof GridAxis2D) {
      return new GridLatLon2D((GridAxis2D) xaxis, (GridAxis2D) yaxis, projection, horizStaggerType);
    }
    throw new RuntimeException();
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  private final @Nullable GridAxis1D xaxis;
  private final @Nullable GridAxis1D yaxis;
  private final Projection projection;
  private final @Nullable String horizStaggerType;

  GridHorizCS(@Nullable GridAxis1D xaxis, @Nullable GridAxis1D yaxis, @Nullable Projection projection,
      @Nullable String horizStaggerType) {
    this.xaxis = xaxis;
    this.yaxis = yaxis;
    this.projection = projection == null ? new LatLonProjection() : projection;
    this.horizStaggerType = horizStaggerType;
  }

  @Override
  @Nullable
  public GridAxis1D getXHorizAxis() {
    return xaxis;
  }

  @Override
  @Nullable
  public GridAxis1D getYHorizAxis() {
    return yaxis;
  }

  @Override
  public Projection getProjection() {
    return projection;
  }

  @Override
  public boolean isLatLon() {
    return projection.isLatLon();
  }

  @Override
  public boolean isGlobalLon() {
    if (!isLatLon()) {
      return false;
    }
    LatLonRect rect = getLatLonBoundingBox();
    return rect.getWidth() >= 360;
  }

  @Override
  public boolean isRegular() {
    if (!isRegularSpatial(getXHorizAxis()))
      return false;
    return isRegularSpatial(getYHorizAxis());
  }

  private boolean isRegularSpatial(GridAxis1D axis) {
    if (axis == null)
      return false;
    return axis.isRegular();
  }

  @Override
  @Nullable
  public String getHorizStaggerType() {
    return horizStaggerType;
  }

  private LatLonRect llbb; // lazy

  @Override
  public LatLonRect getLatLonBoundingBox() {
    if (llbb == null) {
      GridAxis1D xaxis = getXHorizAxis();
      GridAxis1D yaxis = getYHorizAxis();
      if (isLatLon()) {
        double startLat = yaxis.getCoordEdge1(0);
        double startLon = xaxis.getCoordEdge1(0);

        double endLat = yaxis.getCoordEdge2(yaxis.getNcoords() - 1);
        double endLon = xaxis.getCoordEdge2(xaxis.getNcoords() - 1);

        LatLonPoint llpt = LatLonPoint.create(startLat, startLon);
        llbb = new LatLonRect(llpt, endLat - startLat, endLon - startLon);

      } else {
        Projection dataProjection = getProjection();
        ProjectionRect bb = getBoundingBox();
        if (dataProjection != null && bb != null) {
          llbb = dataProjection.projToLatLonBB(bb);
        }
      }
    }
    return llbb;
  }

  ProjectionRect mapArea; // lazy

  @Override
  public ProjectionRect getBoundingBox() {
    if (mapArea == null) {
      mapArea = new ProjectionRect(xaxis.getCoordEdge1(0), yaxis.getCoordEdge1(0),
          xaxis.getCoordEdge2(xaxis.getNcoords() - 1), yaxis.getCoordEdge2(yaxis.getNcoords() - 1));
    }
    return mapArea;
  }

  /**
   * Get the Lat/Lon coordinates of the midpoint of a grid cell, using the x,y indices
   *
   * @param xindex x index
   * @param yindex y index
   * @return lat/lon coordinate of the midpoint of the cell
   */
  public LatLonPoint getLatLon(int xindex, int yindex) {
    double x = xaxis.getCoordMidpoint(xindex);
    double y = yaxis.getCoordMidpoint(xindex);
    return isLatLon() ? LatLonPoint.create(y, x) : getLatLon(x, y);
  }

  private LatLonPoint getLatLon(double xcoord, double ycoord) {
    Projection dataProjection = getProjection();
    return dataProjection.projToLatLon(ProjectionPoint.create(xcoord, ycoord));
  }

  @Override
  public Optional<CoordReturn> findXYindexFromCoord(double x, double y) {
    GridAxis1DHelper xhelper = new GridAxis1DHelper(xaxis);
    GridAxis1DHelper yhelper = new GridAxis1DHelper(yaxis);
    CoordReturn result = new CoordReturn();

    if (xaxis.getAxisType() == AxisType.Lon) {
      x = LatLonPoints.lonNormalFrom(x, xaxis.getStartValue()); // TODO wrong
    }

    result.xindex = xhelper.findCoordElement(x, false);
    result.yindex = yhelper.findCoordElement(y, false);

    if (result.xindex >= 0 && result.xindex < xaxis.getNcoords() && result.yindex >= 0
        && result.yindex < yaxis.getNcoords()) {
      result.xcoord = xaxis.getCoordMidpoint(result.xindex);
      result.ycoord = yaxis.getCoordMidpoint(result.yindex);
      return Optional.of(result);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Get Index Ranges for the given lat, lon bounding box.
   * For projection, only an approximation based on latlon corners.
   *
   * @param rect the requested lat/lon bounding box
   * @return list of 2 Range objects, first y then x.
   */
  public List<Range> getRangesFromLatLonRect(LatLonRect rect) throws InvalidRangeException {
    double minx, maxx, miny, maxy;

    Projection proj = getProjection();
    if (proj != null && !(proj instanceof VerticalPerspectiveView) && !(proj instanceof MSGnavigation)
        && !(proj instanceof Geostationary)) { // LOOK kludge - how to do this generrally ??
      // first clip the request rectangle to the bounding box of the grid
      LatLonRect bb = getLatLonBoundingBox();
      LatLonRect rect2 = bb.intersect(rect);
      if (null == rect2)
        throw new InvalidRangeException("Request Bounding box does not intersect Grid ");
      rect = rect2;
    }

    if (isLatLon()) {
      LatLonPoint llpt = rect.getLowerLeftPoint();
      LatLonPoint urpt = rect.getUpperRightPoint();
      LatLonPoint lrpt = rect.getLowerRightPoint();
      LatLonPoint ulpt = rect.getUpperLeftPoint();

      minx = getMinOrMaxLon(llpt.getLongitude(), ulpt.getLongitude(), true);
      miny = Math.min(llpt.getLatitude(), lrpt.getLatitude());
      maxx = getMinOrMaxLon(urpt.getLongitude(), lrpt.getLongitude(), false);
      maxy = Math.min(ulpt.getLatitude(), urpt.getLatitude());

      // normalize to [minLon,minLon+360]
      MinMax minmaxLon = xaxis.getCoordEdgeMinMax();
      minx = LatLonPoints.lonNormalFrom(minx, minmaxLon.min());
      maxx = LatLonPoints.lonNormalFrom(maxx, minmaxLon.min());

    } else {
      ProjectionRect prect = getProjection().latLonToProjBB(rect); // allow projection to override
      minx = prect.getMinPoint().getX();
      miny = prect.getMinPoint().getY();
      maxx = prect.getMaxPoint().getX();
      maxy = prect.getMaxPoint().getY();
    }

    GridAxis1DHelper xhelper = new GridAxis1DHelper(xaxis);
    GridAxis1DHelper yhelper = new GridAxis1DHelper(yaxis);
    int minxIndex = xhelper.findCoordElement(minx, true);
    int minyIndex = yhelper.findCoordElement(miny, true);

    int maxxIndex = xhelper.findCoordElement(maxx, true);
    int maxyIndex = yhelper.findCoordElement(maxy, true);

    List<Range> list = new ArrayList<>();
    list.add(new Range(Math.min(minyIndex, maxyIndex), Math.max(minyIndex, maxyIndex)));
    list.add(new Range(Math.min(minxIndex, maxxIndex), Math.max(minxIndex, maxxIndex)));
    return list;
  }

  double getMinOrMaxLon(double lon1, double lon2, boolean wantMin) {
    double midpoint = (lon1 + lon2) / 2;
    lon1 = LatLonPoints.lonNormal(lon1, midpoint);
    lon2 = LatLonPoints.lonNormal(lon2, midpoint);

    return wantMin ? Math.min(lon1, lon2) : Math.max(lon1, lon2);
  }
}
