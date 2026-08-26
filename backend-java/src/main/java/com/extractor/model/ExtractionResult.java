package com.extractor.model;

import java.util.Optional;

public record ExtractionResult(
    Optional<FrameResult> visualResult, 
    Optional<Double> audioTimestamp
) {}