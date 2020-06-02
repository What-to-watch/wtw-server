package api.schema.ratings

case class AverageRatingInfo(rating: Double, numRatings: Int)
case class YearlyRatingInfo(year: Int, rating: Double, numRatings: Int)