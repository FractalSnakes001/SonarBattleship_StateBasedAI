package battleship.engine;

/** Answer received after firing a rocket. */
public enum FireResult {
    /** Nothing on that square. */
    MISS,
    /** An enemy boat was hit (this includes the enemy sonar boat, and a square that was already damaged). */
    HIT,
    /** The last undamaged square of an enemy ship was hit: the ship is sunk (its squares are revealed). */
    SUNK,
    /** The enemy sonar boat was sunk (its squares are revealed): its owner can no longer use his sonar. */
    SONAR_BOAT_SUNK;

    /** True for HIT, SUNK and SONAR_BOAT_SUNK. */
    public boolean isHit() { return this != MISS; }
    /** True for SUNK and SONAR_BOAT_SUNK. */
    public boolean isSunk() { return this == SUNK || this == SONAR_BOAT_SUNK; }
}
