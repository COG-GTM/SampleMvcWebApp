using FluentValidation;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Validators
{
    public class BlogValidator : AbstractValidator<BlogDto>
    {
        public BlogValidator()
        {
            RuleFor(b => b.Name)
                .NotEmpty()
                .WithMessage("Name is required")
                .MinimumLength(2)
                .WithMessage("Name must be at least 2 characters")
                .MaximumLength(64)
                .WithMessage("Name cannot exceed 64 characters");

            RuleFor(b => b.EmailAddress)
                .NotEmpty()
                .WithMessage("Email address is required")
                .MaximumLength(256)
                .WithMessage("Email address cannot exceed 256 characters")
                .EmailAddress()
                .WithMessage("Please enter a valid email address");
        }
    }
}
