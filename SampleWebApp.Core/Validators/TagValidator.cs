using FluentValidation;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Validators
{
    public class TagValidator : AbstractValidator<TagDto>
    {
        public TagValidator()
        {
            RuleFor(t => t.Slug)
                .NotEmpty()
                .WithMessage("Slug is required")
                .MaximumLength(64)
                .WithMessage("Slug cannot exceed 64 characters")
                .Matches(@"^\w*$")
                .WithMessage("The slug must not contain spaces or non-alphanumeric characters.");

            RuleFor(t => t.Name)
                .NotEmpty()
                .WithMessage("Name is required")
                .MaximumLength(128)
                .WithMessage("Name cannot exceed 128 characters");
        }
    }
}
