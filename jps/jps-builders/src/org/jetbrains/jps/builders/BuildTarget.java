package org.jetbrains.jps.builders;

/**
 * @author nik
 */
public abstract class BuildTarget {
  private final BuildTargetType myTargetType;

  protected BuildTarget(BuildTargetType targetType) {
    myTargetType = targetType;
  }

  public abstract String getId();

  public final BuildTargetType getTargetType() {
    return myTargetType;
  }
}
