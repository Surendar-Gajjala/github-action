package com.demo.itemintegration.itemdetail.dto;

/**
 * One label / value pair of an object overview (item or part). {@code value} is
 * display-ready and {@code null} when the object has no value; {@code reason} explains a
 * calculated value (e.g. why a risk is "Not Assessed") and is {@code null} otherwise.
 */
public record OverviewField(String label, String value, String reason) {
}
