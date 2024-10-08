/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.dns_rules

import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRuleRecycleItem
import java.util.Date

sealed class DnsRulesMetadata {

    data class RemoteDnsRulesMetadata(
        val name: String,
        val url: String,
        val date: Date,
        val count: Int,
        val size: Long
    ) : DnsRulesMetadata()

    data class LocalDnsRulesMetadata(
        val name: String,
        val date: Date,
        val count: Int,
        val size: Long
    ) : DnsRulesMetadata()

    data class MixedDnsRulesMetadata(
        val count: Int
    )

}