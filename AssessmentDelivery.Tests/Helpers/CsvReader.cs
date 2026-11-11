using System.Collections.Generic;
using System.Text;

namespace AssessmentDelivery.Tests.Helpers
{
    /// <summary>Minimal RFC 4180 reader used to round-trip the roster CSV in tests.</summary>
    public static class CsvReader
    {
        public static List<List<string>> Parse(string csv)
        {
            var records = new List<List<string>>();
            var record = new List<string>();
            var field = new StringBuilder();
            var inQuotes = false;
            var i = 0;
            while (i < csv.Length)
            {
                var c = csv[i];
                if (inQuotes)
                {
                    if (c == '"')
                    {
                        if (i + 1 < csv.Length && csv[i + 1] == '"') { field.Append('"'); i += 2; continue; }
                        inQuotes = false; i++; continue;
                    }
                    field.Append(c); i++; continue;
                }
                if (c == '"' && field.Length == 0) { inQuotes = true; i++; continue; }
                if (c == ',') { record.Add(field.ToString()); field.Clear(); i++; continue; }
                if (c == '\r' && i + 1 < csv.Length && csv[i + 1] == '\n')
                {
                    record.Add(field.ToString()); field.Clear();
                    records.Add(record); record = new List<string>();
                    i += 2; continue;
                }
                field.Append(c); i++;
            }
            if (field.Length > 0 || record.Count > 0)
            {
                record.Add(field.ToString());
                records.Add(record);
            }
            return records;
        }
    }
}
