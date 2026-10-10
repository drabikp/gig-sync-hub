package sk.drabikp.bzscraper.gig.domain;

/** Value object: a point on the map (degrees). */
public record Coordinates(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public Coordinates {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("coordinates must be numbers");
        }
    }

    /** Great-circle distance in km. */
    public double kilometresTo(Coordinates other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude))
                * Math.cos(Math.toRadians(other.latitude)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
