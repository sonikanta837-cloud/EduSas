import React from 'react';
import { Box, Typography, Divider } from '@mui/material';

const Stat = ({ value, label }) => (
  <Box sx={{ textAlign: 'center', px: 4 }}>
    <Typography sx={{ fontSize: '2rem', fontWeight: 800, color: '#14b8a6', lineHeight: 1 }}>
      {value}
    </Typography>
    <Typography sx={{ fontSize: '0.75rem', color: '#64748b', mt: 0.5, fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
      {label}
    </Typography>
  </Box>
);

const Section = ({ heading, children }) => (
  <Box sx={{ mb: 4.5 }}>
    <Typography variant="h6" sx={{ fontWeight: 700, color: '#0f172a', mb: 1.5, fontSize: '1.0625rem' }}>
      {heading}
    </Typography>
    {children}
  </Box>
);

const P = ({ children, sx }) => (
  <Typography sx={{ fontSize: '0.9375rem', color: '#374151', lineHeight: 1.85, mb: 1.5, ...sx }}>
    {children}
  </Typography>
);

const AboutPage = () => (
  <Box sx={{ maxWidth: 820, mx: 'auto', py: 5, px: { xs: 2, sm: 3 } }}>

    {/* ── Page header ── */}
    <Box sx={{ mb: 5 }}>
      <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: '#14b8a6', letterSpacing: '0.12em', textTransform: 'uppercase', mb: 1 }}>
        About Us
      </Typography>
      <Typography variant="h4" sx={{ fontWeight: 800, color: '#0f172a', lineHeight: 1.2, mb: 2 }}>
        SAS KPO Services
      </Typography>
      <Typography sx={{ fontSize: '1.0625rem', color: '#64748b', lineHeight: 1.75, maxWidth: 640 }}>
        A trusted outsourcing partner for accountancy firms across the UK, Australia, and New Zealand —
        delivering precision, reliability, and genuine peace of mind since 2021.
      </Typography>
    </Box>

    {/* ── Inline stats ── */}
    <Box
      sx={{
        display: 'flex', justifyContent: 'center', gap: 0, mb: 5,
        py: 3.5, borderTop: '1px solid #e2e8f0', borderBottom: '1px solid #e2e8f0',
      }}
    >
      <Stat value="2021"    label="Founded"      />
      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />
      <Stat value="10+"     label="Team Members"  />
      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />
      <Stat value="3"       label="Countries Served" />
      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />
      <Stat value="100%"    label="Client Focus"  />
    </Box>

    {/* ── Our Story ── */}
    <Section heading="Our Story">
      <P>
        SAS KPO Services was founded in 2021 by Abhishek Soni, who recognised a recurring problem
        facing accountancy firms of every size: the sheer volume of routine, time-consuming work that
        consumed skilled staff and left little room for the advisory work that truly grows a practice.
        Starting with just two people and a clear sense of purpose, Abhishek set out to build a team
        that could handle that burden — professionally, accurately, and without the overheads of
        in-house hiring.
      </P>
      <P>
        What began as a small operation has steadily grown into a dependable back-office partner for
        firms across the United Kingdom, Australia, and New Zealand. Today, a team of more than ten
        dedicated professionals works closely with clients to make sure their deadlines are met, their
        files are clean, and their own teams are free to focus on what matters most.
      </P>
    </Section>

    {/* ── Pull quote ── */}
    <Box
      sx={{
        borderLeft: '4px solid #14b8a6', pl: 3, py: 1.5, my: 4,
        bgcolor: '#f0fdfa', borderRadius: '0 8px 8px 0',
      }}
    >
      <Typography sx={{ fontSize: '1.0625rem', fontStyle: 'italic', color: '#0f766e', lineHeight: 1.75, fontWeight: 500 }}>
        "Accountancy firms don't need more software — they need someone who genuinely understands
        their work and gets it done right, every time."
      </Typography>
      <Typography sx={{ fontSize: '0.8125rem', color: '#64748b', mt: 1, fontWeight: 600 }}>
        — Abhishek Soni, Founder
      </Typography>
    </Box>

    {/* ── Mission ── */}
    <Section heading="Why We Exist">
      <P>
        Our mission is straightforward: to give accountancy practices back their time. Every firm we
        work with is run by people who trained to advise clients, build relationships, and solve
        complex financial problems. Too often, that expertise gets buried under bookkeeping backlogs,
        tax return queues, and month-end close cycles that stretch well past midnight.
      </P>
      <P>
        We step in precisely at that point. By handling the repeatable, process-driven work with the
        same care and accuracy your clients expect from you, we help you deliver a better service —
        without adding headcount or compromising your margins.
      </P>
    </Section>

    {/* ── What Sets Us Apart ── */}
    <Section heading="What Sets Us Apart">
      <P>
        There is no shortage of outsourcing providers. What distinguishes SAS KPO is not a longer list
        of services — it is the consistency of how we deliver them. Our team is technically trained,
        responsive to feedback, and genuinely invested in the outcomes of the practices we support.
      </P>
      <P>
        We work across the major platforms your practice already uses: Xero, QuickBooks, Sage, MYOB,
        and more. We do not require you to change your systems or processes. We adapt to how you work,
        not the other way around. And when something is unclear, we ask — rather than assume and move on.
      </P>
      <P>
        Our turnaround times are reliable. Our communication is direct. And when a deadline is in play,
        we treat it with the same urgency you would.
      </P>
    </Section>

    {/* ── Cost Benefits ── */}
    <Section heading="The Cost Advantage">
      <P>
        Hiring in-house means salaries, benefits, training, equipment, and the ongoing management
        overhead that comes with growing a team. For many firms, that cost structure makes it difficult
        to scale without significantly increasing their own fees.
      </P>
      <P>
        Working with SAS KPO changes that equation. You access a skilled, experienced team at a
        fraction of what the equivalent in-house hire would cost — with no long-term employment
        commitment and the flexibility to scale the engagement up or down as your workload changes.
        Most of our clients find they can take on new work they previously had to turn away.
      </P>
    </Section>

    {/* ── Our Team ── */}
    <Section heading="Our Team">
      <P>
        Our team is led by qualified accounting professionals with hands-on experience in practice
        environments. They understand the pressure of a tax deadline, the importance of a clean set of
        accounts, and what it means when a client calls with an urgent query the day before filing.
      </P>
      <P>
        Every team member undergoes structured onboarding, regular technical training, and quality
        reviews to ensure the standard of work remains consistently high. We take pride in the depth
        of our team's knowledge — and in the fact that the people working on your files genuinely know
        what they are doing.
      </P>
    </Section>

    {/* ── Security ── */}
    <Section heading="Data Security and Confidentiality">
      <P>
        We understand that the financial data we handle on behalf of your clients is sensitive and
        highly confidential. Data protection is not an afterthought for us — it is built into how we
        operate from day one.
      </P>
      <P>
        All client information is handled under strict confidentiality agreements, transmitted via
        encrypted channels, and stored on secure, access-controlled systems. We are committed to
        maintaining ISO-aligned standards for information security, and we review our practices
        regularly to ensure they keep pace with evolving requirements. Your clients' data is safe
        with us — and we will always be transparent about how it is handled.
      </P>
    </Section>

    {/* ── Flexibility ── */}
    <Section heading="Flexible by Design">
      <P>
        No two practices are alike. A two-partner firm has different needs from a regional mid-tier
        practice, and both have different needs from a solo practitioner managing a growing client
        portfolio. We offer arrangements that reflect that reality.
      </P>
      <P>
        Whether you need support with a specific service line, help clearing a seasonal backlog, or
        a more sustained ongoing partnership, we will work with you to put together an arrangement
        that makes sense. You are not locked into a one-size-fits-all contract.
      </P>
    </Section>

    {/* ── Partnership ── */}
    <Section heading="A Long-Term Partnership">
      <P>
        The practices we work with do not think of us as a vendor. They think of us as part of their
        extended team — people who understand their clients, their workflows, and their standards.
        That kind of relationship takes time to build, and we invest in it deliberately.
      </P>
      <P>
        We measure our success by the success of the firms we support. When your practice grows,
        takes on more clients, and builds a stronger reputation, we have done our job. That alignment
        of interest is at the heart of everything we do at SAS KPO Services.
      </P>
    </Section>

    <Divider sx={{ mt: 2, mb: 3 }} />
    <Typography sx={{ fontSize: '0.8125rem', color: '#94a3b8', textAlign: 'center' }}>
      SAS KPO Services · Founded 2021 · Serving the UK, Australia &amp; New Zealand
    </Typography>

  </Box>
);

export default AboutPage;
