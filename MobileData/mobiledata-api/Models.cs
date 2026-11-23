namespace MobileData.Api;

// DTO: dữ liệu trả về cho ứng dụng, không trả đối tượng SQL trực tiếp.
public sealed record CarrierDto(
    string Code,
    string Name
);

public sealed record PlanDto(
    int Id,
    string CarrierCode,
    string CarrierName,
    string Code,
    string Name,
    decimal Price,
    decimal DataGb,
    string DataType,
    int DurationDays,
    string? Description,
    bool IsPopular
);
