using System;
using System.Collections.Generic;
using System.Linq;

namespace SampleWebApp.Core.UiClasses
{
    public class MultiSelectListType
    {
        public List<KeyValuePair<string, int>> AllPossibleOptions { get; private set; }

        public List<KeyValuePair<string, int>> InitialSelection { get; private set; }

        public string[] FinalSelection { get; set; }

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
