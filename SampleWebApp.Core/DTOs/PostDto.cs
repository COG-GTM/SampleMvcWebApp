using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using SampleWebApp.Core.Common;

namespace SampleWebApp.Core.DTOs
{
    public class PostDto : BaseDto
    {
        [UIHint("HiddenInput")]
        [Key]
        public int PostId { get; set; }

        [MinLength(2), MaxLength(128)]
        public string Title { get; set; }

        [DataType(DataType.MultilineText)]
        [Required]
        public string Content { get; set; }

        public string BloggerName { get; set; }

        [ScaffoldColumn(false)]
        public DateTime LastUpdated { get; set; }

        [UIHint("HiddenInput")]
        public int BlogId { get; set; }

        [ScaffoldColumn(false)]
        public ICollection<TagDto> Tags { get; set; }

        public DropDownListType Bloggers { get; set; }

        public MultiSelectListType UserChosenTags { get; set; }

        public DateTime LastUpdatedUtc => DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc);

        public string TagNames => Tags != null ? string.Join(", ", Tags.Select(x => x.Name)) : string.Empty;

        public PostDto()
        {
            Tags = new List<TagDto>();
            Bloggers = new DropDownListType();
            UserChosenTags = new MultiSelectListType();
        }
    }

    public class DropDownListType
    {
        public List<KeyValuePair<string, string>> KeyValueList { get; private set; }

        [Required]
        public string SelectedValue { get; set; }

        public int? SelectedValueAsInt
        {
            get
            {
                if (int.TryParse(SelectedValue, out int result))
                    return result;
                return null;
            }
        }

        public DropDownListType()
        {
            KeyValueList = new List<KeyValuePair<string, string>>();
        }

        public void SetupDropDownListContent(IEnumerable<KeyValuePair<string, string>> keyValueList, string promptString)
        {
            KeyValueList = keyValueList.ToList();
            if (promptString != null)
                KeyValueList.Insert(0, new KeyValuePair<string, string>(promptString, null));
            else if (KeyValueList.Count > 0)
                SelectedValue = KeyValueList[0].Value;
        }

        public void SetSelectedValue(string valueAsString)
        {
            var foundEntry = KeyValueList.FirstOrDefault(x => x.Value == valueAsString);
            SelectedValue = KeyValueList.Any(x => x.Value == valueAsString)
                ? KeyValueList.First(x => x.Value == valueAsString).Value
                : "--- select from list ---";
        }
    }

    public class MultiSelectListType
    {
        public List<KeyValuePair<string, int>> AllPossibleOptions { get; private set; }

        public List<KeyValuePair<string, int>> InitialSelection { get; private set; }

        public string[] FinalSelection { get; set; }

        public MultiSelectListType()
        {
            AllPossibleOptions = new List<KeyValuePair<string, int>>();
            InitialSelection = new List<KeyValuePair<string, int>>();
        }

        public void SetupMultiSelectList(IEnumerable<KeyValuePair<string, int>> allPossibleOptions,
                                         IEnumerable<KeyValuePair<string, int>> initialSelectionValues)
        {
            AllPossibleOptions = allPossibleOptions.ToList();
            InitialSelection = initialSelectionValues.ToList();
            FinalSelection = InitialSelection.Select(x => x.Value.ToString("D")).ToArray();
        }

        public int[] GetFinalSelectionAsInts()
        {
            var result = new List<int>();
            if (FinalSelection == null)
                return result.ToArray();

            foreach (var intAsString in FinalSelection)
            {
                if (!int.TryParse(intAsString, out int id))
                    throw new ArgumentException("One of the FinalSelection answers was not an integer");
                result.Add(id);
            }
            return result.ToArray();
        }
    }
}
