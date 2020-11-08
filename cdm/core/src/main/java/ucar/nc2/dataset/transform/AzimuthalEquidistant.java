/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.dataset.transform;

import ucar.nc2.AttributeContainer;
import ucar.nc2.constants.CF;
import ucar.nc2.dataset.ProjectionCT;
import ucar.nc2.dataset.TransformType;
import ucar.unidata.geoloc.Earth;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.projection.proj4.EquidistantAzimuthalProjection;

/** AzimuthalEquidistant Projection. */
public class AzimuthalEquidistant extends AbstractProjectionCT implements HorizTransformBuilderIF {

  public String getTransformName() {
    return CF.AZIMUTHAL_EQUIDISTANT;
  }

  public TransformType getTransformType() {
    return TransformType.Projection;
  }

  public ProjectionCT.Builder<?> makeCoordinateTransform(AttributeContainer ctv, String geoCoordinateUnits) {
    readStandardParams(ctv, geoCoordinateUnits);

    // create spherical Earth obj if not created by readStandardParams w radii, flattening
    if (earth == null) {
      if (earth_radius > 0.) {
        // Earth radius obtained in readStandardParams is in km, but Earth object wants m
        earth = new Earth(earth_radius * 1000.);
      } else {
        earth = new Earth();
      }
    }

    Projection proj = new EquidistantAzimuthalProjection(lat0, lon0, false_easting, false_northing, earth);

    return ProjectionCT.builder().setName(ctv.getName()).setAuthority("FGDC").setProjection(proj);
  }
}
