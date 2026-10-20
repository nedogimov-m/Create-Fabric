package com.simibubi.create.content.trains.entity;

/** Literal ServerPlayer native metadata only; setters must remain field-only and callback-free. */
public interface CarriagePlayerReferenceHolder {
    CarriagePlayerReferences.Token create$getCarriageReferenceToken();
    void create$setCarriageReferenceToken(CarriagePlayerReferences.Token token);
}
