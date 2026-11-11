package com.medcare.config;

import com.medcare.util.Cifrado;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda cifrados en la base los campos médicos sensibles (ver util.Cifrado). */
@Converter
public class CifradoConverter implements AttributeConverter<String, String> {
    @Override public String convertToDatabaseColumn(String valor) { return Cifrado.cifrar(valor); }
    @Override public String convertToEntityAttribute(String guardado) { return Cifrado.descifrar(guardado); }
}
