import re
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Tag

CATALOG: list[tuple[str, str, str | None, tuple[str, ...]]] = [
    ("Technology", "Technology", None, ("tech",)),
    ("Artificial Intelligence", "Technology", "AI", ("AI", "A.I.", "Artificial-Intelligence")),
    ("Machine Learning", "Technology", "AI", ("ML",)),
    ("Data Science", "Technology", "AI", ("DataScience",)),
    ("Programming", "Technology", "Programming", ("Coding", "Software Development")),
    ("Web Development", "Technology", "Programming", ("Web Dev",)),
    ("Mobile Development", "Technology", "Programming", ("Mobile Dev",)),
    ("Cybersecurity", "Technology", None, ("Cyber Security", "Infosec")),
    ("Cloud Computing", "Technology", None, ("Cloud",)),
    ("Blockchain", "Technology", None, ()), ("Robotics", "Technology", None, ()),
    ("Internet of Things", "Technology", None, ("IoT",)), ("DevOps", "Technology", None, ()),
    ("Databases", "Technology", None, ("Database",)), ("Open Source", "Technology", None, ()),
    ("Generative AI", "Technology", "Artificial Intelligence", ("GenAI",)),
    ("Agentic AI", "Technology", "Artificial Intelligence", ("AgenticAI",)),
    ("AI Agents", "Technology", "Artificial Intelligence", ("AI Agent", "AI-agent", "AI-agents", "Artificial Intelligence Agents", "LLM Agents", "AIAgents", "Intelligent Agents")),
    ("Large Language Models", "Technology", "Artificial Intelligence", ("LLM", "LLMs")),
    ("Natural Language Processing", "Technology", "Artificial Intelligence", ("NLP",)),
    ("Computer Vision", "Technology", "Artificial Intelligence", ()),
    ("AI Tools", "Technology", "Artificial Intelligence", ()),
    ("AI Automation", "Technology", "Artificial Intelligence", ("AI Workflow Automation",)),
    ("Python", "Technology", "Programming", ("Python Programming",)),
    ("Java", "Technology", "Programming", ()), ("C", "Technology", "Programming", ()),
    ("C++", "Technology", "Programming", ("Cpp",)), ("JavaScript", "Technology", "Programming", ("JS",)),
    ("TypeScript", "Technology", "Programming", ("TS",)), ("React", "Technology", "Programming", ("React.js",)),
    ("Node.js", "Technology", "Programming", ("NodeJS", "Node")), ("Spring Boot", "Technology", "Programming", ()),
    ("Android", "Technology", "Programming", ()), ("Data Structures", "Technology", "Programming", ("DS", "DSA", "Data Structure")),
    ("Algorithms", "Technology", "Programming", ("Algorithm",)),
    ("Competitive Programming", "Technology", "Programming", ("CP",)),
    ("LangChain", "Technology", "Artificial Intelligence", ("Lang Chain",)),
    ("LangGraph", "Technology", "Artificial Intelligence", ("Lang Graph",)),
    ("Education", "Education", None, ()), ("College", "Education", None, ("University",)),
    ("School", "Education", None, ()), ("Exams", "Education", None, ("Exam",)),
    ("Competitive Exams", "Education", None, ()), ("Placements", "Education", "Career", ("Campus Placements", "Placement")),
    ("Internships", "Education", "Career", ("Internship",)), ("Scholarships", "Education", None, ()),
    ("Courses", "Education", None, ("Course",)), ("Certifications", "Education", None, ("Certificates",)),
    ("Research", "Education", None, ()), ("Study Tips", "Education", None, ()),
    ("Career", "Career", None, ()), ("Jobs", "Career", None, ("Job",)), ("Resume", "Career", None, ("CV",)),
    ("Interviews", "Career", None, ("Interview",)), ("Career Advice", "Career", None, ()),
    ("Freelancing", "Career", None, ("Freelance",)), ("Remote Work", "Career", None, ()),
    ("Networking", "Career", None, ()), ("Professional Growth", "Career", None, ()),
    ("Business", "Business", None, ()), ("Startups", "Business", None, ("Startup",)),
    ("Entrepreneurship", "Business", None, ("Entrepreneur",)), ("Marketing", "Business", None, ()),
    ("Sales", "Business", None, ()), ("E-Commerce", "Business", None, ("Ecommerce",)),
    ("Leadership", "Business", None, ()), ("Innovation", "Business", None, ()), ("Small Business", "Business", None, ()),
    ("Sports", "Sports", None, ()), ("Cricket", "Sports", None, ()), ("Football", "Sports", None, ()),
    ("Basketball", "Sports", None, ()), ("Tennis", "Sports", None, ()), ("Badminton", "Sports", None, ()),
    ("Athletics", "Sports", None, ()), ("Esports", "Sports", None, ("E-sports",)),
    ("Entertainment", "Entertainment", None, ()), ("Movies", "Entertainment", None, ("Movie", "Film")),
    ("Web Series", "Entertainment", None, ()), ("Anime", "Entertainment", None, ()),
    ("Gaming", "Entertainment", None, ("Games",)), ("Music", "Entertainment", None, ()),
    ("Memes", "Entertainment", None, ()), ("Comedy", "Entertainment", None, ()),
    ("Books", "Entertainment", None, ("Book",)), ("Podcasts", "Entertainment", None, ("Podcast",)),
    ("Travel", "Travel", None, ()), ("Tourism", "Travel", None, ()), ("Destinations", "Travel", None, ()),
    ("Adventure", "Travel", None, ()), ("Solo Travel", "Travel", None, ()), ("Budget Travel", "Travel", None, ()),
    ("Travel Photography", "Travel", "Photography", ()), ("Lifestyle", "Lifestyle", None, ()),
    ("Food", "Lifestyle", None, ("Cooking", "Cuisine")), ("Fitness", "Lifestyle", None, ()),
    ("Fashion", "Lifestyle", None, ()), ("Photography", "Lifestyle", None, ()),
    ("Hobbies", "Lifestyle", None, ("Hobby",)), ("Pets", "Lifestyle", None, ()), ("Art", "Lifestyle", None, ()),
]


def slugify(value: str) -> str:
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", value.lower().replace("+", " plus ").replace(".", " "))).strip("-")


def alias_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def seed_canonical_tags(db: Session) -> None:
    for name, category, subcategory, aliases in CATALOG:
        slug = slugify(name)
        tag = db.scalar(select(Tag).where(Tag.slug == slug))
        if tag is None:
            db.add(Tag(slug=slug, name=name, category=category, subcategory=subcategory, aliases="|".join(aliases + (name,)), is_canonical=True))
        else:
            tag.name = name
            tag.category = category
            tag.subcategory = subcategory
            tag.aliases = "|".join(aliases + (name,))
            tag.is_canonical = True
    db.flush()


def normalize_tag(db: Session, value: str, allow_custom: bool = False) -> Tag | None:
    cleaned = value.strip().removeprefix("#").strip()
    key = alias_key(cleaned)
    if not key or key in {"public", "followers", "closefriends"}:
        return None
    for tag in db.scalars(select(Tag).where(Tag.is_canonical.is_(True))).all():
        if key in {alias_key(tag.name), alias_key(tag.slug), *(alias_key(alias) for alias in tag.aliases.split("|") if alias)}:
            return tag
    if not allow_custom:
        return None
    slug = slugify(cleaned)
    existing = db.scalar(select(Tag).where(Tag.slug == slug))
    if existing is not None:
        return existing
    custom = Tag(slug=slug, name=" ".join(part.capitalize() for part in cleaned.split()), category="Community", description="User-added topic", aliases=cleaned, is_canonical=False)
    db.add(custom)
    db.flush()
    return custom
