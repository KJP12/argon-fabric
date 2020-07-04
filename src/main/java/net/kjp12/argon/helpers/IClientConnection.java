package net.kjp12.argon.helpers;

public interface IClientConnection {
    boolean isOwnedByWorld();

    SubServer getOwner();

    void setOwner(SubServer world);
}
