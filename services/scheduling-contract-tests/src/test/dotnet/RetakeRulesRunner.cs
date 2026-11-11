// Compiled by RetakeEligibilityContractTest together with the REAL portal sources
// AssessmentDelivery.Business/Rules/{RetakeRules,IRetakeRules,PriorAttempt,RetakeDecision}.cs using Mono (mcs).
// stdin: one case per line  id|waitDays|requestedDate(yyyy-MM-dd)|attempts  where attempts = yyyy-MM-ddTHH:mm:ss@P|F;...  or '-'
// stdout: id|Eligible|Reason|EarliestRetakeDate(yyyy-MM-dd or '-')
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading;
using AssessmentDelivery.Business.Rules;

public static class RetakeRulesRunner
{
    public static void Main()
    {
        // Web.config: <globalization culture="en-GB" uiCulture="en-GB" />
        var culture = new CultureInfo("en-GB");
        Thread.CurrentThread.CurrentCulture = culture;
        Thread.CurrentThread.CurrentUICulture = culture;
        var rules = new RetakeRules();
        string line;
        while ((line = Console.ReadLine()) != null)
        {
            if (line.Trim().Length == 0) continue;
            var p = line.Split('|');
            var attempts = new List<PriorAttempt>();
            if (p[3] != "-")
            {
                foreach (var a in p[3].Split(';'))
                {
                    var parts = a.Split('@');
                    attempts.Add(new PriorAttempt
                    {
                        SessionDate = DateTime.ParseExact(parts[0], "yyyy-MM-dd'T'HH:mm:ss", CultureInfo.InvariantCulture),
                        Passed = parts[1] == "P"
                    });
                }
            }
            var d = rules.Check(attempts, int.Parse(p[1]),
                DateTime.ParseExact(p[2], "yyyy-MM-dd", CultureInfo.InvariantCulture));
            Console.WriteLine("{0}|{1}|{2}|{3}", p[0], d.Eligible ? "true" : "false", d.Reason,
                d.EarliestRetakeDate.HasValue ? d.EarliestRetakeDate.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : "-");
        }
    }
}
