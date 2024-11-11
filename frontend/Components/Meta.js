import Head from "next/head";

export default function Meta({
	title,
	description,
	image,
	url,
	type = "website",
	twitterCardType = "summary_large_image",
	canonicalUrl,
	keywords,
	author = "bytefi.sh",
}) {
	const baseUrl = "https://bytefi.sh";
	const _title = title ? `${title} | bytefi.sh` : "bytefi.sh";
	const _description =
		description || "The Official Bytefi.sh Community Minecraft Server website";
	const _image = image || `${baseUrl}/images/icon.png`;
	const _url = url ? `${baseUrl}${url}` : baseUrl;
	const _keywords =
		keywords || "minecraft, bytefish, server, crossplay, geyser";

	return (
		<Head>
			<title>{_title}</title>
			<meta name="description" content={_description} />
			<meta name="keywords" content={_keywords} />
			<meta name="author" content={author} />
			<meta name="viewport" content="width=device-width, initial-scale=1.0" />
			<meta name="theme-color" content="#5e5e63" />

			<meta property="og:type" content={type} />
			<meta property="og:url" content={_url} />
			<meta property="og:title" content={_title} />
			<meta property="og:description" content={_description} />
			<meta property="og:image" content={_image} />

			<meta name="twitter:card" content={twitterCardType} />
			<meta name="twitter:url" content={_url} />
			<meta name="twitter:title" content={_title} />
			<meta name="twitter:description" content={_description} />
			<meta name="twitter:image" content={_image} />

			{canonicalUrl && <link rel="canonical" href={canonicalUrl} />}

			<link rel="icon" href="/favicon.ico" />
			<link
				rel="apple-touch-icon"
				sizes="180x180"
				href="/apple-touch-icon.png"
			/>
			<link
				rel="icon"
				type="image/png"
				sizes="32x32"
				href="/favicon-32x32.png"
			/>
			<link
				rel="icon"
				type="image/png"
				sizes="16x16"
				href="/favicon-16x16.png"
			/>
		</Head>
	);
}
