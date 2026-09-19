package com.example.exception;

public class UnkownCurrencyException extends RuntimeException {
  public UnkownCurrencyException(String message) {
    super(message);
  }
}
