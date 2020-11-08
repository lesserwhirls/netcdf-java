/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.dataset.transform;

import ucar.nc2.AttributeContainer;
import ucar.nc2.constants.CF;
import ucar.nc2.dataset.ProjectionCT;

/** Create a Mercator Projection from the information in the Coordinate Transform Variable. */
public class Mercator extends AbstractProjectionCT implements HorizTransformBuilderIF {

  @Override
  public String getTransformName() {
    return CF.MERCATOR;
  }

  @Override
  public ProjectionCT.Builder<?> makeCoordinateTransform(AttributeContainer ctv, String geoCoordinateUnits) {
    double par = ctv.findAttributeDouble(CF.STANDARD_PARALLEL, Double.NaN);
    if (Double.isNaN(par)) {
      double scale = ctv.findAttributeDouble(CF.SCALE_FACTOR_AT_PROJECTION_ORIGIN, Double.NaN);
      if (Double.isNaN(scale))
        throw new IllegalArgumentException("Mercator projection must have attribute " + CF.STANDARD_PARALLEL + " or "
            + CF.SCALE_FACTOR_AT_PROJECTION_ORIGIN);
      par = ucar.unidata.geoloc.projection.Mercator.convertScaleToStandardParallel(scale);
    }

    readStandardParams(ctv, geoCoordinateUnits);

    ucar.unidata.geoloc.projection.Mercator proj =
        new ucar.unidata.geoloc.projection.Mercator(lon0, par, false_easting, false_northing, earth_radius);
    return ProjectionCT.builder().setName(ctv.getName()).setAuthority("FGDC").setProjection(proj);
  }
}
