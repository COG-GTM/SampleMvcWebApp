using FluentValidation;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Validators
{
    public class PostValidator : AbstractValidator<PostDto>
    {
        public PostValidator()
        {
            RuleFor(p => p.Title)
                .NotEmpty()
                .WithMessage("Title is required")
                .MinimumLength(2)
                .WithMessage("Title must be at least 2 characters")
                .MaximumLength(128)
                .WithMessage("Title cannot exceed 128 characters")
                .Must(title => title == null || !title.Contains("!"))
                .WithMessage("Sorry, but you can't get too excited and include a ! in the title.")
                .Must(title => title == null || !title.EndsWith("?"))
                .WithMessage("Sorry, but you can't ask a question, i.e. the title can't end with '?'.");

            RuleFor(p => p.Content)
                .NotEmpty()
                .WithMessage("Content is required")
                .Must(content => content == null || !content.Contains(" sheep."))
                .WithMessage("Sorry. Not allowed to end a sentence with 'sheep'.")
                .Must(content => content == null || !content.Contains(" lamb."))
                .WithMessage("Sorry. Not allowed to end a sentence with 'lamb'.")
                .Must(content => content == null || !content.Contains(" cow."))
                .WithMessage("Sorry. Not allowed to end a sentence with 'cow'.")
                .Must(content => content == null || !content.Contains(" calf."))
                .WithMessage("Sorry. Not allowed to end a sentence with 'calf'.");

            RuleFor(p => p.BlogId)
                .GreaterThan(0)
                .When(p => p.Bloggers?.SelectedValueAsInt == null)
                .WithMessage("Please select a valid blog");

            RuleFor(p => p.Tags)
                .Must(tags => tags != null && tags.Count > 0)
                .When(p => p.UserChosenTags?.FinalSelection == null || p.UserChosenTags.FinalSelection.Length == 0)
                .WithMessage("At least one tag must be selected");
        }
    }
}
