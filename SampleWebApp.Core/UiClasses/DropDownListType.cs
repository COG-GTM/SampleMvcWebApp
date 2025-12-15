using System.Collections.Generic;
using System.Linq;

namespace SampleWebApp.Core.UiClasses
{
    public class DropDownListType
    {
        public List<KeyValuePair<string, string>> KeyValueList { get; private set; }

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

        public void SetupDropDownListContent(IEnumerable<KeyValuePair<string, string>> keyValueList, string promptString)
        {
            KeyValueList = keyValueList.ToList();
            if (promptString != null)
                KeyValueList.Insert(0, new KeyValuePair<string, string>(promptString, null));
            else if (KeyValueList.Any())
                SelectedValue = KeyValueList[0].Value;
        }

        public void SetSelectedValue(string valueAsString)
        {
            var foundEntry = KeyValueList.FirstOrDefault(x => x.Value == valueAsString);
            SelectedValue = KeyValueList.Any(x => x.Value == valueAsString)
                ? KeyValueList.First(x => x.Value == valueAsString).Value
                : "--- select from list ---";
        }

        public override string ToString()
        {
            if (SelectedValue != null)
                return string.Format("Selected Value = {0}", SelectedValue);

            if (KeyValueList == null)
                return "KeyValue list has not been set up yet.";

            return string.Format("{0} items in list, but no selected value", KeyValueList.Count);
        }
    }
}
