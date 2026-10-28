package app.sthira.engine

/** Shared data types. x and y are normalised 0..1 with y pointing DOWN (same as the TypeScript engine). */
data class Landmark(val x: Double, val y: Double, val z: Double, val visibility: Double)

data class PoseFrame(val t: Double, val landmarks: List<Landmark>)

data class VideoGeometry(val width: Double, val height: Double)
